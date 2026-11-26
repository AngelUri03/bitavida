// Development-only MNB trainer. Built-ins only; reads local fixtures and writes only with --write.
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const stop = new Set('a al de del el la las los un una unos unas y o con en mi mis me te tu tus su sus este esta esto estos estas ese esa esos esas yo usted ustedes nos le les se oye explicas explicame favor gracias quiero saber puedes podrias'.split(' '));
export function normalize(text){return text.toLowerCase().normalize('NFKD').replace(/[\p{M}\p{Cf}]+/gu,'').replace(/[^a-z0-9]+/g,' ').trim().replace(/\s+/g,' ');}
export function stem(word){
 for(const ending of ["nos","los","las","me","te","se","lo","la"])if(word.endsWith(ending)){const base=word.slice(0,-ending.length);if(/(ar|er|ir)$/.test(base)) {word=base;break;}}
 for(const ending of ["amientos","imientos","amiento","imiento","aciones","acion","mente","ando","iendo","ados","adas","idos","idas","aron","ieron","ado","ada","ido","ida","ar","er","ir","es","os","as","s"]){if(word.length-ending.length>=4&&word.endsWith(ending))return word.slice(0,-ending.length);}
 return word;
}
export function words(text){return (normalize(text.slice(0,500)).match(/[a-z]+/g)??[]).flatMap(w=>['porque','porq','pq','xq','pk','xk','porke','porqe','porqu'].includes(w)?['por','que']:['q','k','ke'].includes(w)?['que']:w==='pa'?['para']:w==='x'?['por']:[w]);}
export function tokens(text){return words(text).filter(w=>w.length>1&&!stop.has(w)).map(stem).slice(0,80);}
export function features(ts){return [...ts.map(w=>'w:'+w),...ts.slice(1).map((w,i)=>'b:'+ts[i]+'_'+w),...ts.filter(w=>w.length>=4).flatMap(w=>{const s='_'+w+'_';return Array.from({length:s.length-3},(_,i)=>'c:'+s.slice(i,i+4));})];}
function oneEdit(a,b){if(Math.abs(a.length-b.length)>1)return false;let i=0,j=0,n=0;while(i<a.length&&j<b.length){if(a[i]===b[j]){i++;j++;continue;}if(++n>1)return false;if(a.length===b.length&&i+1<a.length&&j+1<b.length&&a[i]===b[j+1]&&a[i+1]===b[j]){i+=2;j+=2;continue;}if(a.length>=b.length)i++;if(b.length>=a.length)j++;}return n+(a.length-i)+(b.length-j)===1;}
export function build(rows, cw=0.1, bw=0.1, threshold=.75, margin=.2, coverageMin=.8) {
 const labels=[...new Set(rows.map(r=>r[0]))],counts={},docs=labels.map(l=>rows.filter(r=>r[0]===l).length);
 for(const [label,q]of rows){const c=labels.indexOf(label);for(const f of features(tokens(q))){counts[f]??=labels.map(()=>0);counts[f][c]++;}}
 const vocab=Object.keys(counts),wv=vocab.filter(v=>v.startsWith('w:')).map(v=>v.slice(2));
 const totals=labels.map((_,c)=>vocab.reduce((s,f)=>s+counts[f][c],0));
 function predict(q){
  if(q.length>500)return {topic:null,suggestions:[]};
  const ts=tokens(q).map(w=>{if(counts['w:'+w]||w.length<4)return w;const cs=wv.filter(x=>x.length>=4&&oneEdit(w,x)).map(x=>({w:x,n:counts['w:'+x].reduce((a,b)=>a+b,0)})).sort((a,b)=>b.n-a.n);return cs.length===1||cs.length>1&&cs[0].n>=2*cs[1].n?cs[0].w:w;});
  const unique=[...new Set(ts)],known=unique.filter(w=>counts['w:'+w]);
  const mass=ws=>ws.reduce((n,w)=>n+Math.min(w.length,12),0),coverage=mass(known)/(mass(unique)||1);
  if(known.length<2||coverage<coverageMin)return {topic:null,suggestions:[],coverage};
  const freq={};for(const f of features(ts))freq[f]=(freq[f]??0)+1;
  const scores=labels.map((_,c)=>Object.entries(freq).reduce((sum,[f,n])=>sum+(counts[f]?Math.log((counts[f][c]+.1)/(totals[c]+.1*vocab.length))*Math.min(n,2)*(f[0]==='w'?1:f[0]==='b'?bw:cw):0),Math.log(docs[c]/rows.length)));
  const mx=Math.max(...scores),ex=scores.map(s=>Math.exp(s-mx)),sum=ex.reduce((a,b)=>a+b,0),rank=ex.map((e,i)=>({p:e/sum,t:labels[i]})).sort((a,b)=>b.p-a.p),bg=ex.at(-1)/sum;
  const requiredPosterior=rank[0].t.startsWith('MEDICATION_')?.90:threshold;
  return {topic:rank[0].t!=='__UNKNOWN__'&&rank[0].p>=requiredPosterior&&rank[0].p-rank[1].p>=margin?rank[0].t:null,posterior:rank[0].p,coverage,suggestions:bg<.5?rank.filter(r=>r.t!=='__UNKNOWN__'&&r.p>=.015).slice(0,2).map(r=>r.t):[]};
 }
 return {predict,tokens,counts,labels,docs,features};
}

const readRows = url => readFileSync(url, 'utf8').trim().split(/\r?\n/)
 .filter(line => line.trim() && !line.trimStart().startsWith('#')).map(line => line.split('\t'));
const trainingIndex = process.argv.indexOf('--training');
const outputIndex = process.argv.indexOf('--output');
const trainingFile = trainingIndex >= 0 ? resolve(process.argv[trainingIndex + 1]) : new URL('./training.tsv', import.meta.url);
const modelOutput = outputIndex >= 0 ? resolve(process.argv[outputIndex + 1]) : new URL('../../app/src/main/res/raw/bitavida_education_model.tsv', import.meta.url);
const rows = readRows(trainingFile);
const model = build(rows);
export function predict(question) { return model.predict(question); }
const artifact = ['BITAVIDA_MNB\t3\t0.1', model.labels.join('\t'), model.docs.join('\t'),
    ...Object.keys(model.counts).sort().map(f => [f, ...model.counts[f]].join('\t'))].join('\n') + '\n';
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
 if (process.argv.includes('--write')) {
  const target = modelOutput;
  writeFileSync(target, artifact, 'utf8');
  console.log(JSON.stringify({written:true, documents:rows.length, topics:model.labels.length-1,
    features:Object.keys(model.counts).length, bytes:Buffer.byteLength(artifact)}));
 } else if (process.argv.includes('--check')) {
  const bundled = readFileSync(modelOutput, 'utf8').replace(/\r\n/g,'\n');
  if (bundled !== artifact) throw new Error('Packaged model differs from trainer');
  console.log(JSON.stringify({reproducible:true, documents:rows.length, topics:model.labels.length-1,
    features:Object.keys(model.counts).length, bytes:Buffer.byteLength(artifact)}));
 } else if (['--calibrate','--evaluate','--audit','--acceptance','--dataset'].some(flag=>process.argv.includes(flag))) {
  const file=process.argv.includes('--calibrate')?'./calibration.tsv':
    process.argv.includes('--evaluate')?'../../app/src/test/resources/education/evaluation.tsv':
    process.argv.includes('--acceptance')?'../../app/src/test/resources/education/acceptance.tsv':
    '../../app/src/test/resources/education/audit.tsv';
  const datasetIndex = process.argv.indexOf('--dataset');
  const dataset = datasetIndex >= 0 ? resolve(process.argv[datasetIndex + 1]) : new URL(file, import.meta.url);
  const results=readRows(dataset).map(([label,q])=>({
    expected:label==='__UNKNOWN__'?null:label, q, ...predict(q)}));
  console.log(JSON.stringify({
    known:results.filter(r=>r.expected).length, correct:results.filter(r=>r.expected&&r.topic===r.expected).length,
    wrongAccepted:results.filter(r=>r.expected&&r.topic&&r.topic!==r.expected).length,
    rejected:results.filter(r=>r.expected&&!r.topic).length, unknown:results.filter(r=>!r.expected).length,
    unknownRejected:results.filter(r=>!r.expected&&!r.topic).length,
    unknownAccepted:results.filter(r=>!r.expected&&r.topic).length,
    incorrect:results.filter(r=>r.topic!==r.expected)},null,2));
 } else process.stdout.write(artifact);
}
