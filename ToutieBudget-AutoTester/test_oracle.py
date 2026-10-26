import unittest
from finance_oracle import Ledger,cents
class FinanceTests(unittest.TestCase):
    def test_decimal_precision(self):
        self.assertEqual(cents('0,01'),1)
        self.assertEqual(cents('12.345'),1235)
        self.assertEqual(cents('2500'),250000)
        with self.assertRaises(ValueError):cents('NaN')
    def test_transfer_conserves_money(self):
        m=Ledger();m.account('rouge',250000);m.account('bleu',50000)
        for _ in range(48):m.transfer('rouge','bleu',125)
        self.assertEqual(m.accounts,{'rouge':244000,'bleu':56000})
        self.assertEqual(sum(m.accounts.values()),300000)
    def test_edit_delete_reversibility(self):
        m=Ledger();m.account('cheques',250000);m.envelope('épicerie',80000)
        m.post('t1','cheques',-12500,'épicerie')
        self.assertEqual(m.accounts['cheques'],237500)
        m.edit('t1','cheques',-17500,'épicerie')
        self.assertEqual(m.accounts['cheques'],232500)
        m.undo('t1')
        self.assertEqual(m.accounts['cheques'],250000)
        self.assertEqual(m.envelopes['épicerie'],80000)
    def test_ownership_invariants(self):
        m=Ledger();m.account('rouge',100);m.account('bleu',0)
        with self.assertRaises(ValueError):m.transfer('rouge','rouge',10)
        with self.assertRaises(ValueError):m.transfer('rouge','bleu',-1)
        with self.assertRaises(KeyError):m.post('x','absent',100)
        self.assertEqual(sum(m.accounts.values()),100)
if __name__=='__main__':unittest.main()
