import {createCipheriv,randomBytes} from 'node:crypto';
import {readFileSync,writeFileSync} from 'node:fs';
const secret=process.env.PRIVATE_APK_KEY;
if(!/^[a-f0-9]{64}$/.test(secret||''))throw new Error('Missing private package encryption key');
const iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',Buffer.from(secret,'hex'),iv);
const payload=Buffer.concat([cipher.update(readFileSync('android/app/build/outputs/apk/release/app-release.apk')),cipher.final()]);
writeFileSync('private-setup.apk.enc',Buffer.concat([Buffer.from('RACHELS-SETUP-1\n'),iv,cipher.getAuthTag(),payload]));
