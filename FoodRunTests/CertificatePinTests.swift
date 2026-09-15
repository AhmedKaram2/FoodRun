import XCTest
import CryptoKit
import Security
@testable import FoodRun

final class CertificatePinTests: XCTestCase {
    // Public self-signed certificate only; its disposable private key is not shipped.
    private let encodedCertificate = "MIIDDzCCAfegAwIBAgIUSapGKB+vnbYcM2m66P2hMMr7h1owDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UEAwwMRm9vZFJ1bi1UZXN0MB4XDTI2MDkxNTA4NDI1NFoXDTI2MTAxNTA4NDI1NFowFzEVMBMGA1UEAwwMRm9vZFJ1bi1UZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2CbWaRdWXz1j1stR58tjN9nMa+UMycm1VExHDVEquZuYOOYni+qtiPHFzl2s6FUgaFAQt45sKeCrMfxoa7fhLu1pIWI2LeYr0Jasa9gs49iA/jCRL/yZPI+C9AMrTOASwjDFgrDKLnMjQNfuZIfVqvAJXC7prgn1MYvItJQ37Br6fT7zh1s2t/VWzpQMcvBNs0A8gT/q34UahKfHyILpbglbYGAx5098/DVSBIVWQaTy/pXx4mTb9BxqsuyrS/ZiqnAD7Bk+WjybsUlw9j+5oofdI0YefBkp6ei2a/4rhNjPAWyDl5VNQf5llPGYHYd5aw1dutOjBxP83FSajb/6gQIDAQABo1MwUTAdBgNVHQ4EFgQU1CPbSBd8ac9PCKw6R7Ahl+rboU8wHwYDVR0jBBgwFoAU1CPbSBd8ac9PCKw6R7Ahl+rboU8wDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAQgS2JurgobocO1pMm+NoRDroKuJ5I7Q2q0gUHVC7/U7iFqZo9KlLpMLlmVjPihLW9ivZ6Uw66QYWFXdop7P3ELiNXt2zTK7Y/EvqUTXEaUw688IGdNk/BctEgDo2ffswqmwNCQaz1lYP5kYFwZKO3sopNDxHmNp3g47tmgbLVj22bA8aSAc1+3D5vt/VlIzNNgv9S84STGRCmSKhMBtkMeWRvujlcOnOEUfEqNSNiBzKkkChd4D6ttkqa7AgL6sXRRRj5ORJtEA7vj5jQHL7mLCJQ3WA+7FSnenkFWRNVy3Jzys5JoQvoVp02vp8TJS1DOPD9JrdpRSSlbZbwviorg=="

    func testPinnedCertificateChecksIdentityAndValidityDates() throws {
        let data = try XCTUnwrap(Data(base64Encoded: encodedCertificate))
        let certificate = try XCTUnwrap(SecCertificateCreateWithData(nil, data as CFData))
        let fingerprint = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        for (date, expected) in [("2026-09-20T12:00:00Z", true), ("2025-01-01T12:00:00Z", false), ("2050-01-01T12:00:00Z", false)] {
            var trust: SecTrust?
            XCTAssertEqual(SecTrustCreateWithCertificates(certificate, SecPolicyCreateBasicX509(), &trust), errSecSuccess)
            let value = try XCTUnwrap(trust)
            let verificationDate = try XCTUnwrap(ISO8601DateFormatter().date(from: date))
            SecTrustSetVerifyDate(value, verificationDate as CFDate)
            XCTAssertEqual(PinnedHubSession.accepts(certificate: certificate, trust: value, fingerprint: fingerprint), expected, date)
        }
        var trust: SecTrust?
        SecTrustCreateWithCertificates(certificate, SecPolicyCreateBasicX509(), &trust)
        XCTAssertFalse(PinnedHubSession.accepts(certificate: certificate, trust: try XCTUnwrap(trust), fingerprint: String(repeating: "0", count: 64)))
    }
}
