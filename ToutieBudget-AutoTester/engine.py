#!/usr/bin/env python3
"""ToutieBudget Android E2E: deterministic action plans, optional finance assertions,
stateful discovery/fuzz, real run artifacts. No direct app APIs; ADB only.
Never marks a business step PASS unless postcondition is checked.
"""
import argparse, collections, datetime as dt, hashlib, html, json, random, re, subprocess, sys, time, traceback, xml.etree.ElementTree as ET
from pathlib import Path
APP='com.xburnsx.toutiebudget'
DANGER=re.compile(r'(?i)réinitialis|reinitialis|supprimer.*(donn|compte google)|déconnexion|deconnexion|sign out|logout|effacer.*tout|factory|désinstaller|uninstall')
ERROR=re.compile(r'(?i)une erreur|erreur inattendue|a cessé|ne répond pas|fatal exception|exception non gérée|crash')

def adb(serial,*args, timeout=45, binary=False):
    p=subprocess.run(['adb']+(['-s',serial] if serial else [])+list(args),capture_output=True,timeout=timeout)
    if p.returncode: raise RuntimeError(f'adb ({p.returncode}): '+p.stderr.decode(errors='replace')[-1500:])
    return p.stdout if binary else p.stdout.decode(errors='replace')

def scan(serial):
    adb(serial,'shell','uiautomator','dump','/sdcard/toutie_auto.xml')
    raw=adb(serial,'shell','cat','/sdcard/toutie_auto.xml')
    pos=raw.find('<?xml'); assert pos>=0,'Android UI XML unavailable'
    root=ET.fromstring(raw[pos:]); out=[]
    for n in root.iter('node'):
        x=n.attrib; b=list(map(int,re.findall(r'\d+',x.get('bounds',''))))
        if len(b)!=4 or b[2]<=b[0] or b[3]<=b[1]: continue
        out.append({'text':x.get('text',''),'desc':x.get('content-desc',''),'id':x.get('resource-id',''), 'class':x.get('class',''),'enabled':x.get('enabled')=='true','clickable':x.get('clickable')=='true','bounds':b,'center':[(b[0]+b[2])//2,(b[1]+b[3])//2]})
    return out,raw[pos:]

def match(nodes, target, exact=False):
    if isinstance(target,list):
        for t in target:
            m=match(nodes,t,exact)
            if m:return m
        return None
    if not target:return None
    key=str(target).casefold()
    for n in nodes:
        if any(v.casefold()==key for v in (n['text'],n['desc'],n['id']) if v):return n
    if not exact:
        for n in nodes:
            if any(key in v.casefold() for v in (n['text'],n['desc'],n['id']) if v):return n
    return None

class Blocked(Exception):pass

class Engine:
    def __init__(self,args):
        self.a=args; self.rng=random.Random(args.seed); self.root=Path(args.output);self.root.mkdir(parents=True,exist_ok=True)
        self.events=(self.root/'events.jsonl').open('a',encoding='utf8');self.results=[];self.features=collections.defaultdict(lambda:collections.Counter());self.recent=[]
        self.case_no=0;self.prev_logs='';self.running=True
    def event(self,**o):
        o={'at':dt.datetime.now().isoformat(timespec='seconds'),**o};self.events.write(json.dumps(o,ensure_ascii=False)+'\n');self.events.flush()
    def evidence(self,name):
        p=self.root/'evidence'/f'{self.case_no:05}_{name[:32]}';p.mkdir(parents=True,exist_ok=True)
        try:(p/'screen.png').write_bytes(adb(self.a.serial,'exec-out','screencap','-p',binary=True))
        except Exception:pass
        try:(p/'ui.xml').write_text(scan(self.a.serial)[1],encoding='utf8')
        except Exception:pass
        try:(p/'logcat.txt').write_text(adb(self.a.serial,'logcat','-d','-t','500'),encoding='utf8')
        except Exception:pass
        (p/'repro.json').write_text(json.dumps(self.recent[-40:],ensure_ascii=False,indent=2),encoding='utf8')
        return str(p)
    def ensure_app(self):
        current=adb(self.a.serial,'shell','dumpsys','activity','activities')
        if APP not in current:
            adb(self.a.serial,'shell','monkey','-p',APP,'1');time.sleep(2)
        # Existing Google test account session only; never auth by script.
    def get(self):
        n,_=scan(self.a.serial)
        labels=' '.join((x['text']+' '+x['desc']) for x in n)
        if ERROR.search(labels):self.event(kind='ui_error_text',message=labels[:1000])
        return n
    def tap(self,target,optional=False):
        nodes=self.get();node=match(nodes,target)
        if not node:
            if optional:return False
            raise Blocked('Contrôle absent: '+str(target))
        if not node['enabled']: raise Blocked('Contrôle désactivé: '+str(target))
        if DANGER.search(node['text']+' '+node['desc']):raise Blocked('Action protégée: '+str(target))
        x,y=node['center'];adb(self.a.serial,'shell','input','tap',str(x),str(y));time.sleep(self.a.delay)
        return True
    def input(self,target,value):
        nodes=self.get();node=match(nodes,target)
        if not node:raise Blocked('Champ absent: '+str(target))
        x,y=node['center'];adb(self.a.serial,'shell','input','tap',str(x),str(y))
        if not re.fullmatch(r'[A-Za-z0-9 .,_-]{0,120}',str(value)):
            raise Blocked('Saisie ADB non supportée, ne pas envoyer une valeur transformée: '+str(value))
        adb(self.a.serial,'shell','input','keyevent','123')
        for _ in range(80):adb(self.a.serial,'shell','input','keyevent','67')
        if str(value):adb(self.a.serial,'shell','input','text',str(value).replace(' ','%s'))
        adb(self.a.serial,'shell','input','keyevent','4');time.sleep(self.a.delay)
    def verify(self,step):
        nodes=self.get(); target=step.get('target');kind=step['kind']
        if kind=='visible' and not match(nodes,target):raise AssertionError('Attendu visible: '+str(target))
        if kind=='absent' and match(nodes,target):raise AssertionError('Attendu absent: '+str(target))
        if kind=='money':
            expected=int(step['cents'])/100
            normalized=lambda s:re.sub(r'\s+','',s).replace('$','').replace('\u00a0','').replace(',','.')
            candidates=[]
            for n in nodes:
                v=normalized(n['text']+' '+n['desc']);candidates.extend(re.findall(r'-?\d+(?:\.\d{1,2})?',v))
            nums=[]
            for c in candidates:
                try:nums.append(float(c))
                except ValueError:pass
            if not any(abs(v-expected)<0.005 for v in nums):raise AssertionError(f'{expected:.2f}$ absent de l’interface; valeurs détectées: {nums[:50]}')
        if kind=='text_contains':
            if not any(str(step['value']).casefold() in (n['text']+' '+n['desc']).casefold() for n in nodes):raise AssertionError('Texte attendu absent: '+str(step['value']))
        if kind=='app_alive':
            activity=adb(self.a.serial,'shell','dumpsys','activity','activities')
            if APP not in activity:raise AssertionError('App non active; crash ou fermeture')
        if kind=='no_crash':
            raw=adb(self.a.serial,'logcat','-d','-t','700','-v','brief')
            if re.search(r'FATAL EXCEPTION.*?(?:\n.*?){0,8}Process: com\.xburnsx\.toutiebudget',raw,re.S):raise AssertionError('FATAL EXCEPTION ToutieBudget dans logcat')
    def action(self,s):
        action=s['action'];target=s.get('target')
        if action=='tap':self.tap(target)
        elif action=='input':self.input(target,str(s['value']))
        elif action=='back':adb(self.a.serial,'shell','input','keyevent','4');time.sleep(self.a.delay)
        elif action=='restart':adb(self.a.serial,'shell','am','force-stop',APP);adb(self.a.serial,'shell','monkey','-p',APP,'1');time.sleep(3)
        elif action=='navigate':
            self.ensure_app()
            if not self.tap(target,optional=True):
                adb(self.a.serial,'shell','input','keyevent','4');time.sleep(.4)
                if not self.tap(target,optional=True):raise Blocked('Navigation inaccessible: '+str(target))
        elif action=='scroll':
            for _ in range(int(s.get('times',1))):adb(self.a.serial,'shell','input','swipe','500','1550','500','440','360');time.sleep(self.a.delay)
        elif action=='assert':self.verify(s)
        elif action=='pause':time.sleep(min(30,float(s.get('seconds',1))))
        elif action=='snapshot':self.evidence('checkpoint')
        elif action=='month_next':
            # Explicitly require a UI month selector; do NOT change Android clock,
            # whose Google auth/TLS changes would invalidate the test.
            if not self.tap(['Mois suivant','Mois prochain','Suivant'],optional=True):raise Blocked('Sélecteur de mois non identifié. Chronologie NON simulée.')
        elif action=='probe':
            node=match(self.get(),target)
            if not node:raise Blocked('Fonction absente: '+str(target))
        elif action=='fuzz_input':
            self.input(target,str(s['value']))
            self.verify({'kind':'app_alive'})
        else:raise ValueError('Action inconnue '+action)
    def run_case(self,c):
        self.case_no+=1;self.recent=[];t0=time.time();status='PASS';message='';tested=0
        for idx,step in enumerate(c['steps']):
            self.recent.append(step)
            try:
                self.action(step)
                self.event(kind='step',case=c['id'],index=idx,step=step,status='OK')
                if step['action']=='assert':tested+=1
            except Blocked as e:
                status='BLOCKED';message=str(e);self.event(kind='step',case=c['id'],index=idx,status=status,error=message);break
            except Exception as e:
                status='FAIL';message=str(e);self.event(kind='step',case=c['id'],index=idx,status=status,error=message,trace=traceback.format_exc()[-1500:]);break
        if status=='PASS' and tested==0:status='UNVERIFIED';message='Aucune assertion métier après les actions'
        proof=self.evidence(c['id']) if status in ('FAIL','BLOCKED') else ''
        rec={'id':c['id'],'feature':c['feature'],'status':status,'assertions':tested,'duration_s':round(time.time()-t0,2),'message':message,'evidence':proof}
        self.results.append(rec);self.features[c['feature']][status]+=1;self.event(kind='case',**rec)
        print(f"[{len(self.results)}] {status:10} {c['id']} {message[:110]}",flush=True)
        return rec
    def report(self):
        counts=collections.Counter(r['status'] for r in self.results)
        obj={'started_for':'ToutieBudget Android','package':APP,'seed':self.a.seed,'counts':counts,'features':{k:dict(v) for k,v in self.features.items()},'cases':self.results,'important':'PASS = postcondition explicitly verified. BLOCKED and UNVERIFIED are NOT a success. 48 calendar months require a working in-app month navigation; advancing display does not change system time or recurring scheduler.'}
        (self.root/'rapport.json').write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf8')
        lines=[]
        for r in self.results:
            lines.append('<tr>'+''.join('<td>'+html.escape(str(r[k]))+'</td>' for k in ['id','feature','status','assertions','message','evidence'])+'</tr>')
        body='<meta charset="utf-8"><style>body{font:15px system-ui;margin:30px}td,th{border:1px solid #ccc;padding:8px}table{border-collapse:collapse}tr:nth-child(even){background:#eee}</style><h1>ToutieBudget — Rapport E2E Android</h1><p>'+html.escape(str(counts))+'</p><p>BLOCKED/UNVERIFIED ≠ PASS. Aucun accès direct à PocketBase.</p><table><tr>'+''.join('<th>'+k+'</th>' for k in ['id','feature','status','assertions','message','evidence'])+'</tr>'+''.join(lines)+'</table>'
        (self.root/'rapport.html').write_text(body,encoding='utf8')
        self.events.flush()
    def main(self,cases):
        adb(self.a.serial,'start-server');self.ensure_app()
        for c in cases:
            try:self.run_case(c)
            except Exception as e:self.event(kind='runner_fault',case=c['id'],error=str(e))
            if len(self.results)%25==0:self.report()
        self.report();self.events.close()
        print('\nRapport:',self.root/'rapport.html')

def cases_for(args):
    cases=json.loads(Path(args.catalog).read_text(encoding='utf8'))['cases']
    if args.mode in ('years','all'):
        # Each month is separate and reported; no fake calendar clock advancement.
        for month in range(args.months):
            yy=month//12+1;mm=month%12+1
            checks=[{'action':'navigate','target':'Budget'},{'action':'assert','kind':'app_alive'},{'action':'month_next'}, {'action':'assert','kind':'app_alive'}, {'action':'navigate','target':'Comptes'}, {'action':'assert','kind':'app_alive'},{'action':'restart'}, {'action':'assert','kind':'app_alive'}]
            cases.append({'id':f'LONG-{yy:02d}-{mm:02d}','feature':'long_duration','steps':checks})
    if args.mode in ('destroy','all'):
        # Field fuzz probes are intentionally non-submitting: no destructive action on real data.
        rng=random.Random(args.seed)
        values=['0','0.01','-1','999999999','00000001','1.999','12345678901234567890','NaN','Infinity','','a'*80]
        fields=[('Compte','Nom'),('Compte','Solde'),('Ajouter','Montant'),('Ajouter','Note'),('Budget',"Nom de l'enveloppe")]
        for i in range(args.fuzz):
            screen,field=fields[i%len(fields)];v=values[rng.randrange(len(values))]
            cases.append({'id':f'DEST-{i:06d}','feature':'destructive_input','steps':[{'action':'navigate','target':screen},{'action':'fuzz_input','target':field,'value':v},{'action':'assert','kind':'app_alive'},{'action':'back'}]})
    if args.mode=='smoke':cases=[c for c in cases if c['feature']=='navigation']
    return cases

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--serial');p.add_argument('--catalog',default='catalog.json');p.add_argument('--output',default='resultats/run');p.add_argument('--seed',type=int,default=20261026);p.add_argument('--months',type=int,default=48);p.add_argument('--fuzz',type=int,default=2000);p.add_argument('--delay',type=float,default=.6);p.add_argument('--mode',choices=['all','smoke','years','destroy'],default='all')
    a=p.parse_args();Engine(a).main(cases_for(a))
