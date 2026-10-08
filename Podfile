require_relative 'mobileApp/node_modules/react-native/scripts/react_native_pods'
ENV['REACT_NATIVE_NODE_MODULES_DIR'] = File.join(__dir__, 'mobileApp/node_modules')
platform :ios, '17.0'
prepare_react_native_project!
project 'FoodRun.xcodeproj'
target 'FoodRun' do
  use_react_native!(
    :path => './mobileApp/node_modules/react-native',
    :app_path => File.join(Pod::Config.instance.installation_root,'mobileApp'),
    :config_file_dir => File.join(Pod::Config.instance.installation_root,'mobileApp')
  )
  pod 'RNSVG', :path => './mobileApp/node_modules/react-native-svg'
  pod 'react-native-safe-area-context', :path => './mobileApp/node_modules/react-native-safe-area-context'
  target 'FoodRunTests' do
    inherit! :search_paths
  end
  post_install do |installer|
    react_native_post_install(installer,'./mobileApp/node_modules/react-native',:mac_catalyst_enabled => false)
    installer.pods_project.targets.each do |target|
      target.build_configurations.each { |config| config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '17.0' }
    end
  end
end
