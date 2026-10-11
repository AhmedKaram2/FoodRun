#!/usr/bin/env python3
"""Run after assembleRelease, before building/deploying the website."""
import hashlib, json, re, sys
from pathlib import Path
root=Path(__file__).resolve().parent.parent
apk=Path(sys.argv[1]) if len(sys.argv)>1 else root/'androidApp/build/outputs/apk/release/androidApp-release.apk'
config=(root/'androidApp/build.gradle.kts').read_text()
version=re.search(r'versionName = "([^"]+)"',config).group(1)
code=int(re.search(r'versionCode = (\d+)',config).group(1))
release={'android':{'versionCode':code,'versionName':version,'url':f'https://github.com/AhmedKaram2/FoodRun/releases/download/v{version}/FoodRun-Android-{version}.apk','sha256':hashlib.sha256(apk.read_bytes()).hexdigest()}}
(root/'webApp/public/app-release.json').write_text(json.dumps(release,indent=2)+'\n')
print(f'Android release manifest written for {version} ({code})')
