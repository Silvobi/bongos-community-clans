// Usage: node scripts/prepare-clan-browser-fixture.cjs <fresh-test-world-directory>
const fs=require('node:fs'), path=require('node:path');
const {randomUUID}=require('node:crypto');
if (process.argv.length!==3) throw new Error('Provide a fresh disposable test world directory.');
const root=path.resolve(process.argv[2]), file=path.join(root,'bongo-community-clans.json');
if(fs.existsSync(file)) throw new Error('Refusing to overwrite an existing clan database.');
const state={schemaVersion:1,clans:{},users:{},invitations:{}};
for(let n=1;n<=28;n++) {
    const id=randomUUID(), members={};
    for(let m=0;m<(n===28?28:1);m++) {
        const uid=randomUUID();members[uid]={id:uid,name:`Fixture${n}_${m}`,role:m===0?'FOUNDER':'MEMBER'};
    }
    state.clans[id]={id,name:n===28?'Żółwie':`Fixture${String(n).padStart(2,'0')}`,tag:`F${n}`,createdAt:n*1000,members};
}
fs.mkdirSync(root,{recursive:true});fs.writeFileSync(file,JSON.stringify(state,null,2),{flag:'wx'});
console.log(file);
