"""Oracle financier indépendant; zéro réseau, zéro PocketBase, montants en cents."""
from dataclasses import dataclass, field
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP


def cents(value):
    x=Decimal(str(value).replace(' ', '').replace(',', '.').replace('$',''))
    if not x.is_finite(): raise ValueError('montant non fini')
    return int((x*100).quantize(Decimal('1'),rounding=ROUND_HALF_UP))

@dataclass
class Ledger:
    accounts:dict=field(default_factory=dict)
    envelopes:dict=field(default_factory=dict)
    transactions:dict=field(default_factory=dict)
    entries:list=field(default_factory=list)
    def account(self,name,opening=0):
        if name in self.accounts:raise ValueError('compte existe')
        self.accounts[name]=opening
        self.check()
    def envelope(self,name,opening=0):
        if name in self.envelopes:raise ValueError('enveloppe existe')
        self.envelopes[name]=opening
        self.check()
    def post(self,tid,account,amount,envelope=None):
        if tid in self.transactions: raise ValueError('transaction existe')
        if account not in self.accounts:raise KeyError(account)
        if envelope and envelope not in self.envelopes:raise KeyError(envelope)
        self.accounts[account]+=amount
        if envelope:self.envelopes[envelope]+=amount
        self.transactions[tid]=(account,amount,envelope)
        self.entries.append(('post',tid,account,amount,envelope))
        self.check()
    def undo(self,tid):
        a,v,e=self.transactions.pop(tid)
        self.accounts[a]-=v
        if e:self.envelopes[e]-=v
        self.entries.append(('undo',tid))
        self.check()
    def edit(self,tid,account,amount,envelope=None):
        self.undo(tid);self.post(tid,account,amount,envelope)
    def transfer(self,source,dest,amount):
        if source==dest:raise ValueError('même compte')
        if amount<0:raise ValueError('transfert négatif')
        if source not in self.accounts or dest not in self.accounts:raise KeyError('compte')
        before=sum(self.accounts.values())
        self.accounts[source]-=amount;self.accounts[dest]+=amount
        self.entries.append(('transfer',source,dest,amount))
        assert sum(self.accounts.values())==before
        self.check()
    def allocate(self,src,dst,amount):
        if src==dst:raise ValueError('même enveloppe')
        if amount<0:raise ValueError('allocation négative')
        if src not in self.envelopes or dst not in self.envelopes:raise KeyError('enveloppe')
        before=sum(self.envelopes.values())
        self.envelopes[src]-=amount;self.envelopes[dst]+=amount
        self.entries.append(('allocate',src,dst,amount))
        assert sum(self.envelopes.values())==before
        self.check()
    def check(self):
        assert all(type(x) is int for x in self.accounts.values())
        assert all(type(x) is int for x in self.envelopes.values())
    def snapshot(self):return {'accounts':dict(self.accounts),'envelopes':dict(self.envelopes),'transactions':dict(self.transactions)}

if __name__=='__main__':
    import random
    r=random.Random(20261026)
    for trial in range(200):
        m=Ledger();m.account('rouge',250000);m.account('bleu',50000);m.envelope('épicerie',50000);m.envelope('loyer',90000)
        for i in range(200):
            amount=r.randrange(0,100000)
            if r.randrange(3)==0:m.transfer('rouge','bleu',amount)
            elif r.randrange(2)==0:m.allocate('épicerie','loyer',amount)
            else:
                m.post('x'+str(i),'rouge',-amount,'épicerie');m.undo('x'+str(i))
        m.check()
    print('Oracle: 40 000 cycles de mutations OK')
