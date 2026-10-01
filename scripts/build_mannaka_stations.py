"""国土交通省の全国鉄道GeoJSONから同梱用の駅一覧を作成する。"""
import argparse, hashlib, json, math, zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('archive', type=Path)
parser.add_argument('--output', type=Path, default=Path('app/src/main/assets/mannaka'))
args = parser.parse_args()
with zipfile.ZipFile(args.archive) as archive:
    names = [name for name in archive.namelist() if '/UTF-8/' in name and name.endswith('_Station.geojson')]
    if len(names) != 1:
        raise ValueError('UTF-8の駅GeoJSONが一意に見つかりません。')
    features = json.loads(archive.read(names[0]))['features']
rows = {}
for feature in features:
    properties = feature['properties']
    coordinates = feature['geometry']['coordinates']
    if feature['geometry']['type'] == 'MultiLineString':
        coordinates = [point for line in coordinates for point in line]
    longitude = sum(point[0] for point in coordinates) / len(coordinates)
    latitude = sum(point[1] for point in coordinates) / len(coordinates)
    if not (20 < latitude < 46 and 122 < longitude < 154):
        raise ValueError('国内範囲外の駅があります。')
    identifier = properties['N02_005c']
    # 同じ駅コードに複数の路線がある場合は詳細をまとめる。
    detail = properties['N02_004'] + '・' + properties['N02_003']
    if identifier in rows:
        rows[identifier][-1].add(detail)
    else:
        rows[identifier] = [properties['N02_005'], round(latitude, 6), round(longitude, 6), {detail}]
text = ''.join('\t'.join([identifier, name, str(lat), str(lon), ' / '.join(sorted(details))]) + '\n'
               for identifier, (name, lat, lon, details) in sorted(rows.items()))
if len(rows) < 9000 or '\ufffd' in text or len(text.encode()) > 2_000_000:
    raise ValueError('駅数・文字コード・容量の検証に失敗しました。')
args.output.mkdir(parents=True, exist_ok=True)
data = text.encode('utf-8')
(args.output / 'stations.tsv').write_bytes(data)
metadata = {'source': '国土交通省 国土数値情報 鉄道データ 2025年度',
 'url': 'https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-N02-2025.html',
 'license': 'CC BY 4.0', 'reference_date': '2025-12-31', 'retrieved': '2026-10-01',
 'processing': '駅の線形座標を平均して代表点に変換。駅コードで統合し、駅名・路線・運営会社・座標のみを収録。',
 'count': len(rows), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
 'archive_sha256': hashlib.sha256(args.archive.read_bytes()).hexdigest()}
(args.output / 'stations-source.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('stations', len(rows), 'bytes', len(data), 'sha256', metadata['sha256'])
