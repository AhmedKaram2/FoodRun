#!/usr/bin/env python3
"""Compare record-prefix SQL on fixture data; never opens a FoodRun database."""
import json
import sqlite3
from time import perf_counter

database = sqlite3.connect(':memory:')
database.execute('CREATE TABLE account_records (key TEXT PRIMARY KEY, body TEXT NOT NULL)')
database.executemany('INSERT INTO account_records VALUES (?, ?)',
    [(f'unrelated:{index:06}', 'fixture') for index in range(20_000)] +
    [(f'wallet:payment:{index:06}', 'fixture') for index in range(20)])
queries = {
    'before': ('SELECT key,body FROM account_records WHERE substr(key,1,?)=?', (15, 'wallet:payment:')),
    'after': ('SELECT key,body FROM account_records WHERE key>=? AND key<? ORDER BY rowid', ('wallet:payment:', 'wallet:payment;')),
}
result = {'rows': 20_020, 'matching_rows': 20, 'iterations': 250, 'fixture_only': True}
for name, (query, arguments) in queries.items():
    started = perf_counter()
    for iteration in range(result['iterations']):
        rows = database.execute(query, arguments).fetchall()
        assert len(rows) == result['matching_rows']
    result[name] = {'milliseconds': round((perf_counter() - started) * 1000, 2),
        'query_plan': [row[3] for row in database.execute('EXPLAIN QUERY PLAN ' + query, arguments)]}
result['speedup'] = round(result['before']['milliseconds'] / result['after']['milliseconds'], 1)
print(json.dumps(result, indent=2))
