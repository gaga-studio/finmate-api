"""Generate the small, deterministic synthetic dataset used by the demo and tests.
No real people, bank records, network calls or random inputs.
"""
import csv
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'demo' / 'bundles'
headers = ['transaction_id','날짜','시간','내용','금액','cashflow_bucket','대분류','소분류','rule_id','결제수단','메모']
for number in range(1, 25):
    external = f'P{number:04}'
    folder = root / external
    folder.mkdir(parents=True, exist_ok=True)
    profile = dict(persona_id=external, synthetic_name=f'합성 사용자 {number}', age=27,
        cohort='MZ', job='직장인', archetype='계획형', region='서울', household_type='1인',
        household_size=1, monthly_income_krw=3000000, income_band='250~350만원',
        income_regularity='정기', target_monthly_spend_krw=1200000,
        target_saving_rate=0.2, target_investment_rate=0.1, invest_participation=True,
        risk_score=3, risk_attitude='중립', data_range='2026-01~2026-07')
    (folder/'profile.json').write_text(json.dumps(profile, ensure_ascii=False, indent=2)+'\n')
    with (folder/'ledger.csv').open('w', newline='', encoding='utf-8-sig') as file:
        writer = csv.writer(file)
        writer.writerow(headers)
        for month in range(1, 8):
            # Eight hand-defined rows per month; merchant includes CSV-sensitive text.
            for index, (day, merchant, amount, flow, major, minor) in enumerate([
                (1,'데모 급여',3000000,'소득','근로소득','급여'),
                (3,'데모 청약\n자동이체',-200000,'저축','저축','예적금'),
                (5,'데모 투자',-100000,'투자','투자','주식'),
                (7,'데모 식당',-10000*number,'소비','식비','외식'),
                (14,'데모 카페, 1호점',-5000,'소비','카페/간식','커피'),
                (21,'데모 교통',-15000,'소비','교통','대중교통'),
                (27,'데모 마트',-30000-month*100,'소비','생활','생필품'),
                (28 if month==2 else 30 if month in (4,6) else 31,'데모 저녁',-12000,'소비','식비','외식'),
            ], 1):
                writer.writerow([f'{external}-M{month:02}-{index}',f'2026-{month:02}-{day:02}',
                    '12:00:00',merchant,amount,flow,major,minor,'demo-v1','데모 카드','합성 데이터'])
print('24 personas, 1,344 ledger rows, January-July 2026')
