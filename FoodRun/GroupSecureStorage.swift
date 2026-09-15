import Foundation
import CryptoKit
import Security

/// Encrypted atomic snapshots. A missing file is different from unreadable saved data.
final class GroupSecureStorage {
    static let maximumBytes = 16 * 1_024 * 1_024
    private let directory: URL?
    private let keyProvider: () throws -> SymmetricKey
    private let lock = NSLock()

    init(directory: URL? = nil, keyProvider: @escaping () throws -> SymmetricKey = GroupSecureStorage.deviceKey) {
        self.directory = directory
        self.keyProvider = keyProvider
    }

    func read(_ name: String) -> String {
        lock.lock()
        defer { lock.unlock() }
        do {
            let url = try file(name)
            guard FileManager.default.fileExists(atPath: url.path) else { return "" }
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? .max
            guard size <= Self.maximumBytes + 64 else { throw CocoaError(.fileReadTooLarge) }
            let encrypted = try Data(contentsOf: url)
            guard encrypted.count <= Self.maximumBytes + 64 else { throw CocoaError(.fileReadTooLarge) }
            let data = try AES.GCM.open(AES.GCM.SealedBox(combined: encrypted), using: keyProvider())
            return String(data: data, encoding: .utf8) ?? "storage_unavailable"
        } catch { return "storage_unavailable" }
    }

    func write(_ value: String, for name: String) -> Bool {
        guard value.utf8.count <= Self.maximumBytes else { return false }
        lock.lock()
        defer { lock.unlock() }
        do {
            let sealed = try AES.GCM.seal(Data(value.utf8), using: keyProvider())
            guard let data = sealed.combined else { return false }
            try data.write(to: file(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch { return false }
    }

    private func file(_ name: String) throws -> URL {
        guard !name.isEmpty, name.count <= 100,
              name.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }) else {
            throw CocoaError(.fileWriteInvalidFileName)
        }
        var folder = try directory ?? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("FoodRunGroups", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var attributes = URLResourceValues()
        attributes.isExcludedFromBackup = true
        try folder.setResourceValues(attributes)
        return folder.appendingPathComponent(name + ".enc")
    }

    static func deviceKey() throws -> SymmetricKey {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.karim.foodrun.groups",
            kSecAttrAccount as String: "storage"
        ]
        var lookup = query
        lookup[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(lookup as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data, data.count == 32 {
            return SymmetricKey(data: data)
        }
        guard status == errSecItemNotFound else { throw GroupKeychainError.status(status) }
        let key = SymmetricKey(size: .bits256)
        var insert = query
        insert[kSecValueData as String] = key.withUnsafeBytes { Data($0) }
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let inserted = SecItemAdd(insert as CFDictionary, nil)
        // Another adapter can create the same device key while this one was reading.
        if inserted == errSecDuplicateItem { return try deviceKey() }
        guard inserted == errSecSuccess else { throw GroupKeychainError.status(inserted) }
        return key
    }
}

enum GroupKeychainError: Error { case status(OSStatus) }
