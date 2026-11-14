#!/usr/bin/env python3
"""Cloud helper: preserve proxy routing and system CA trust; no credentials stored."""
import os,pathlib,subprocess,sys,urllib.parse
root=pathlib.Path(__file__).resolve().parents[1]
env=os.environ.copy()
env.setdefault('JAVA_HOME','/workspace/toolchains/jdk-21.0.9+10')
env.setdefault('ANDROID_HOME','/workspace/toolchains/android')
env.setdefault('GRADLE_USER_HOME','/workspace/gradle-cache')
env.setdefault('ANDROID_USER_HOME','/workspace/toolchains/android-user')
args=['-Dandroid.user.home=/workspace/toolchains/android-user']
proxy=urllib.parse.urlparse(env.get('HTTPS_PROXY',''))
if proxy.hostname:
 for scheme in ('http','https'):
  args += [f'-D{scheme}.proxyHost={proxy.hostname}',f'-D{scheme}.proxyPort={proxy.port or 80}']
 args += ['-Dhttp.nonProxyHosts=localhost|127.*|[::1]']
trust=pathlib.Path('/etc/ssl/certs/java/cacerts')
if trust.exists():args += ['-Djavax.net.ssl.trustStore='+str(trust)]
launcher=root/'gradlew'
if not launcher.exists():launcher=pathlib.Path('/workspace/toolchains/gradle-9.6.0/bin/gradle')
raise SystemExit(subprocess.call([str(launcher),*args,*sys.argv[1:]],cwd=root,env=env))
