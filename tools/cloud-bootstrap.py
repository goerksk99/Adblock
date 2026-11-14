#!/usr/bin/env python3
"""Idempotent Cloud tool provisioning. Application sources and lockfiles stay unchanged."""
import hashlib,os,pathlib,shutil,subprocess,tarfile,tempfile,urllib.parse,zipfile
ROOT=pathlib.Path('/workspace/toolchains')
SDK=ROOT/'android'
JDK=ROOT/'jdk-21.0.9+10'
ROOT.mkdir(parents=True,exist_ok=True)
def fetch(url,dest,algorithm,expected):
    subprocess.run(['curl','-fLsS','--retry','2','--max-time','240',url,'-o',str(dest)],check=True)
    if hashlib.new(algorithm,dest.read_bytes()).hexdigest()!=expected:raise RuntimeError('Artifact checksum mismatch: '+dest.name)
with tempfile.TemporaryDirectory(dir=ROOT) as temp:
    stage=pathlib.Path(temp)
    if not (JDK/'bin/javac').exists():
        url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz'
        checksum=stage/'jdk.sha256'
        subprocess.run(['curl','-fLsS','--max-time','30',url+'.sha256.txt','-o',str(checksum)],check=True)
        archive=stage/'jdk.tar.gz';fetch(url,archive,'sha256',checksum.read_text().split()[0])
        with tarfile.open(archive) as source:source.extractall(ROOT,filter='data')
    for index,(name,checksum,relative) in enumerate([
        ('commandlinetools-linux-13114758_latest.zip','5fdcc763663eefb86a5b8879697aa6088b041e70','cmdline-tools/19.0'),
        ('build-tools_r37_linux.zip','70954e99f4c3d9d46ee70fa32624672fe7cd6ebe','build-tools/37.0.0'),
        ('platform-tools_r37.0.3-linux.zip','e1579b288a7ae4e9bc28dedec6a154d78b3c04ed','platform-tools'),
    ]):
        target=SDK/relative
        if target.exists():continue
        archive=stage/name;fetch('https://dl.google.com/android/repository/'+name,archive,'sha1',checksum)
        unpack=stage/str(index)
        with zipfile.ZipFile(archive) as source:
            for entry in source.infolist():
                if not (unpack/entry.filename).resolve().is_relative_to(unpack.resolve()):raise RuntimeError('Unsafe archive path')
            source.extractall(unpack)
            for entry in source.infolist():
                mode=entry.external_attr>>16
                if mode:(unpack/entry.filename).chmod(mode)
        children=list(unpack.iterdir())
        if len(children)!=1:raise RuntimeError('Unexpected SDK archive layout')
        target.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(children[0]),str(target))
if not (SDK/'licenses/android-sdk-license').exists():
    # The standard SDK Manager shows and accepts official SDK license agreements.
    env=os.environ.copy();env['JAVA_HOME']=str(JDK);env['ANDROID_USER_HOME']=str(ROOT/'android-user')
    env['JAVA_OPTS']='-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Dandroid.user.home='+str(ROOT/'android-user')
    proxy=urllib.parse.urlparse(env.get('HTTPS_PROXY',''));args=[]
    if proxy.hostname:args=['--proxy=http','--proxy_host='+proxy.hostname,'--proxy_port='+str(proxy.port or 80)]
    result=subprocess.run([str(SDK/'cmdline-tools/19.0/bin/sdkmanager'),'--sdk_root='+str(SDK),*args,'--licenses'],input='y\n'*100,text=True,env=env)
    if result.returncode:raise RuntimeError('SDK license setup failed')
for tool in [JDK/'bin/javac',SDK/'platform-tools/adb',SDK/'build-tools/37.0.0/aapt2']:
    subprocess.run([str(tool),'version' if tool.name!='javac' else '-version'],check=True)
print('Cloud tools verified. Gradle installs API 37.0 through the supported SDK auto-download when needed.')
