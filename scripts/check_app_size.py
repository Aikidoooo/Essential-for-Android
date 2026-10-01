"""APK容量とCPU別インストール容量の概算を400MBの条件で検査する。"""
import argparse, json, zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('directory', type=Path)
parser.add_argument('--output', type=Path)
args = parser.parse_args()
limit = 400_000_000
rows = []
for path in sorted(args.directory.rglob('*.apk')):
    if 'androidTest' in str(path):
        continue
    with zipfile.ZipFile(path) as archive:
        libraries = {}
        for item in archive.infolist():
            if item.filename.startswith('lib/') and item.compress_type != zipfile.ZIP_STORED:
                abi = item.filename.split('/')[1]
                libraries[abi] = libraries.get(abi, 0) + item.file_size
        # APKに加えて、対象CPUのライブラリ展開と64MBのデータ・キャッシュ枠を計上する。
        estimated = path.stat().st_size + max(libraries.values(), default=0) + 64_000_000
        abis = {item.filename.split('/')[1] for item in archive.infolist() if item.filename.startswith('lib/')}
        universal = len(abis) > 1
        row = {'apk': path.name, 'apk_bytes': path.stat().st_size,
               'estimated_install_with_64mb_data': estimated,
               'cpu_specific': not universal, 'apk_pass': path.stat().st_size <= limit,
               'estimated_install_pass': estimated <= limit}
        rows.append(row)
        if not row['apk_pass'] or not row['estimated_install_pass']:
            raise SystemExit('400MB条件を超えています: ' + path.name)
if not rows:
    raise SystemExit('検査対象APKがありません。')
text = json.dumps({'limit_bytes': limit, 'note': '展開容量は概算。配布する全APKを本体と64MBのデータ枠で検査。利用者の保存データ・他機能の増加は含めない。', 'artifacts': rows}, ensure_ascii=False, indent=2)
if args.output:
    args.output.write_text(text + '\n', encoding='utf-8')
print(text)
