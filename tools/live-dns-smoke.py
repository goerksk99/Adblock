#!/usr/bin/env python3
"""Read-only host probe for Quad9 DoH; does not claim Android VPN validation."""
import json,pathlib,struct,subprocess,tempfile,time
for name,qtype in [('example.com',1),('example.com',28)]:
    question=b''.join(bytes([len(label)])+label.encode('ascii') for label in name.split('.'))+b'\0'+struct.pack('!HH',qtype,1)
    query=struct.pack('!6H',0x5149,0x0100,1,0,0,0)+question
    with tempfile.TemporaryDirectory() as directory:
        root=pathlib.Path(directory);(root/'query').write_bytes(query)
        start=time.perf_counter()
        subprocess.run(['curl','-fLsS','--max-time','20','https://dns.quad9.net/dns-query','-H','Accept: application/dns-message','-H','Content-Type: application/dns-message','--data-binary','@'+str(root/'query'),'-o',str(root/'answer')],check=True)
        data=(root/'answer').read_bytes();ident,flags,qd,an,ns,ar=struct.unpack('!6H',data[:12])
        assert ident==0x5149 and flags&0x8000 and flags&15==0 and an>0
        print(json.dumps({'domain':name,'type':qtype,'answers':an,'response_bytes':len(data),'milliseconds':round((time.perf_counter()-start)*1000,1)}))
