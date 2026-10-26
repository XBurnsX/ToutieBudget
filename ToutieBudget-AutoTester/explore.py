"""Exploration intelligente ADB à profondeur bornée, avec journal/rejeu et exclusions sûres.
Ce n'est PAS un oracle métier. Les découvertes reçoivent le statut DISCOVERED.
"""
import argparse, collections, datetime, hashlib, json, random, re, time, traceback
from pathlib import Path
from engine import adb, scan, Engine, APP, DANGER, ERROR, Blocked

DANGEROUS=re.compile(r'(?i)(déconnex|reinitial|réinitial|effacer|delete|supprimer|archiver|fermer le compte|google|exporter|importer|install|mise à jour|réinitialiser|vider)',re.I)
NAVWORDS=('budget','comptes','ajouter','stats','catégories','paramètres','virement','historique','archives','enveloppe')
class Explorer:
    def __init__(self,args):
        self.args=args;self.engine=Engine(args);self.rng=random.Random(args.seed);self.history=[];self.seen=set();self.edges=[];self.errors=[]
        self.out=Path(args.output);self.out.mkdir(parents=True,exist_ok=True)
    def snapshot(self):
        nodes,xml=scan(self.args.serial)
        state=[(x['text'],x['desc'],x['id'],x['class']) for x in nodes]
        key=hashlib.sha256(json.dumps(state,ensure_ascii=False).encode()).hexdigest()[:16]
        return key,nodes
    def eligible(self,n):
        label=(n['text']+' '+n['desc']).strip()
        return n['enabled'] and n['clickable'] and label and not DANGEROUS.search(label) and not DANGER.search(label) and n['center'][0]>0
    def explore(self):
        self.engine.ensure_app()
        queue=collections.deque([([],0)])
        steps=0
        while queue and steps<self.args.max_actions:
            path,depth=queue.popleft()
            try:
                adb(self.args.serial,'shell','am','force-stop',APP)
                adb(self.args.serial,'shell','monkey','-p',APP,'1');time.sleep(self.args.delay*3)
                for target in path:
                    self.engine.tap(target)
                key,nodes=self.snapshot()
                if key in self.seen:continue
                self.seen.add(key)
                labels=[x['text'] or x['desc'] for x in nodes if x['text'] or x['desc']]
                self.history.append({'state':key,'depth':depth,'path':path,'labels':labels})
                if ERROR.search(' '.join(labels)):
                    self.errors.append({'state':key,'path':path,'type':'visible_error','labels':labels})
                    self.engine.case_no+=1;self.engine.evidence('error_'+key)
                if depth>=self.args.depth:continue
                options=[n for n in nodes if self.eligible(n)]
                options.sort(key=lambda x:(not any(k in (x['text']+' '+x['desc']).casefold() for k in NAVWORDS),x['text'],x['desc']))
                for n in options[:self.args.max_branches]:
                    name=n['desc'] or n['text']
                    self.edges.append({'from':key,'action':name,'depth':depth})
                    queue.append((path+[name],depth+1))
                    steps+=1
                    if steps>=self.args.max_actions:break
            except Exception as e:
                self.errors.append({'path':path,'type':type(e).__name__,'detail':str(e)})
            finally:
                obj={'screens':self.history,'edges':self.edges,'errors':self.errors,'summary':{'unique_screens':len(self.seen),'queued_actions':steps,'errors':len(self.errors),'outcome':'DISCOVERY_ONLY_NOT_FINANCIAL_VALIDATION'}}
                (self.out/'exploration.json').write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf8')
        self.engine.events.close()
        print('Écrans distincts:',len(self.seen),'actions explorées:',steps,'erreurs:',len(self.errors))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--serial');p.add_argument('--seed',type=int,default=20261026);p.add_argument('--output',default='resultats/explore');p.add_argument('--depth',type=int,default=5);p.add_argument('--max-actions',type=int,default=1000);p.add_argument('--max-branches',type=int,default=18);p.add_argument('--delay',type=float,default=0.75)
    Explorer(p.parse_args()).explore()
