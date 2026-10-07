#!/usr/bin/env python3
"""Rekonstruksi blueprint UI dari dump Blutter.
Input: path file asm (e.g. asm/animelovers/widgets/series_card.dart)
Output: alur widget + konstanta (EdgeInsets/Radius/Color/String) yang di-resolve.
"""
import re, sys, collections

PP = '/tmp/opencode/blutter_out/pp.txt'

# --- index ObjectPool: pp+0xADDR -> isi blok ---
pool = {}
cur_addr, cur_line = None, []
for line in open(PP, errors='replace'):
    m = re.match(r'\[pp\+0x([0-9a-f]+)\] (.*)', line)
    if m:
        if cur_addr is not None:
            pool[cur_addr] = '\n'.join(cur_line)
        cur_addr = m.group(1)
        cur_line = [m.group(2)]
    elif cur_addr is not None and (line.startswith('  ') or line.startswith('    ')):
        cur_line.append(line.rstrip())
    elif cur_addr is not None:
        pool[cur_addr] = '\n'.join(cur_line)
        cur_addr, cur_line = None, []
if cur_addr is not None:
    pool[cur_addr] = '\n'.join(cur_line)

def fmt_val(v):
    v = v.strip()
    if v.startswith('double('):
        f = float(v[7:-1])
        return str(int(f)) if f == int(f) else str(f)
    if v.startswith('int('):
        return str(int(v[4:-1], 16)) if '0x' in v else v[4:-1]
    return v

def resolve(block):
    """ubah isi blok pool jadi ekspresi Flutter yang manusiawi."""
    if block is None: return None
    if block.startswith('String: '):
        return 'String ' + block[8:]
    m = re.match(r'Obj!EdgeInsets@', block)
    if m:
        raw = re.findall(r'off_(8|10|18|20): (?:double|int)\(([\d.]+)\)', block)
        if raw:
            vals = {k: fmt_val('double(%s)' % v) for k, v in raw}
            l = vals.get('8', '0'); t = vals.get('10', '0')
            r_ = vals.get('18', l); b_ = vals.get('20', t)
            if l == t == r_ == b_: return f'EdgeInsets.all({l})'
            if l == r_ and t == b_: return f'EdgeInsets.symmetric(horizontal: {l}, vertical: {t})'
            return f'EdgeInsets.fromLTRB({l}, {t}, {r_}, {b_})'
        return 'EdgeInsets?'
    if block.startswith('Obj!Radius@'):
        vals = re.findall(r'off_(?:8|10): (?:double|int)\(([\d.]+)\)', block)
        if vals and fmt_val('double(%s)' % vals[0]) == fmt_val('double(%s)' % vals[-1]):
            return f'Radius.circular({fmt_val("double(%s)" % vals[0])})'
        return 'Radius.elliptical(...)'
    if block.startswith('Obj!BorderRadius@'):
        refs = re.findall(r'off_(?:8|c|10|14)_Obj!Radius@([0-9a-f]+)', block)
        rr = [e for e in (resolve(pool.get(r)) for r in set(refs)) if e]
        return f'BorderRadius({", ".join(sorted(set(rr)))})' if rr else 'BorderRadius?'
    if block.startswith('Obj!Color@'):
        nums = re.findall(r'off_(?:8|10|18|20): (?:double|int)\(([\d.]+)\)', block)
        if len(nums) >= 4:
            a, r, g, b = [min(255, round(float(x) * 255)) for x in nums[:4]]
            if a == 255: return f'Color(0xFF{r:02X}{g:02X}{b:02X})'
            return f'Color(0x{a:02X}{r:02X}{g:02X}{b:02X}) // a={round(a/255*100)}%'
        return 'Color?'
    if block.startswith('Obj!TextStyle@'):
        fs = re.search(r'off_20: (\d+)', block)
        col = re.search(r'off_c_Obj!Color@([0-9a-f]+)', block)
        w = re.search(r'off_24_Obj!FontWeight@([0-9a-f]+)', block)
        parts = []
        if fs: parts.append('fontSize: ' + fs.group(1))
        if col: parts.append('color: ' + (resolve(pool.get(col.group(1))) or '?'))
        if w:
            wv = re.search(r'off_8: int\((0x[0-9a-f]+)\)', pool.get(w.group(1), ''))
            if wv: parts.append('fontWeight: w' + str(int(wv.group(1), 16)))
        return 'TextStyle(' + ', '.join(parts) + ')'
    if block.startswith('Obj!FontWeight@'):
        wv = re.search(r'off_8: int\((0x[0-9a-f]+)\)', block)
        return 'FontWeight.w' + str(int(wv.group(1), 16)) if wv else 'FontWeight?'
    if block.startswith('Obj!SizedBox@'):
        vals = re.findall(r'off_[0-9a-f]+: (\d+)', block)
        if len(vals) >= 2:
            return f'SizedBox(w: {vals[0]}, h: {vals[1]})'
        if len(vals) == 1:
            return f'SizedBox(h: {vals[0]})'
        return 'SizedBox?'
    if block.startswith('IMM: double('):
        n = re.match(r'IMM: double\(([\d.]+)\)', block)
        if n:
            f = float(n.group(1))
            return 'double ' + (str(int(f)) if f == int(f) else str(f))
        return None
    m = re.match(r'(Obj!\w+)@', block)
    if m: return m.group(1) + '@…'
    return None

# --- parse asm ---
def blueprint(path):
    events = []
    in_build = False
    for line in open(path, errors='replace'):
        if re.match(r'\s+_ build\(', line) or re.match(r'\s+build\(', line):
            in_build = True
        if in_build and re.match(r'(class |\s+_ [a-zA-Z]+\(\/\* No info)', line):
            # method baru / kelas baru -> berhenti kalau bukan build pertama
            if events and not line.strip().startswith('_ build'):
                pass
        if not in_build: continue

        # alokasi widget high level: "r0 = Container()"
        m = re.search(r'r\d+ = ([A-Z][A-Za-z_]+)\(\)', line)
        if m:
            if m.group(1) in NOISE: continue
            if m.group(1) in CONSTS:
                events.append(('c', m.group(1), None)); continue
            events.append(('W', m.group(1), None)); continue
        # Instance_X dari pool
        m = re.search(r'Instance_([A-Za-z_]+).*\[pp\+0x([0-9a-f]+)\]', line)
        if m:
            addr = m.group(2)
            ev = resolve(pool.get(addr))
            events.append(('W', m.group(1), ev if ev and not ev.startswith(m.group(1)) else None))
            continue
        # pool ref umum: "add x16, PP, #.. ; [pp+0x..] Obj!..."
        m = re.search(r'\[pp\+0x([0-9a-f]+)\] (Obj!\w+|String|IMM)', line)
        if m:
            ev = resolve(pool.get(m.group(1)))
            if ev and not ev.startswith(('Obj!', 'IMM')):
                kind = 'C' if ev.split('(')[0] in ('EdgeInsets', 'Radius', 'BorderRadius', 'Color') else 'K'
                if ev.startswith('String'): kind = 'T'
                if ev.startswith(('TextStyle', 'FontWeight')): kind = 'S'
                if ev.startswith('SizedBox'): kind = 'W'
                events.append((kind, None, ev))
            continue
        # panggilan bermakna: Color::withOpacity, TextStyle::..., BorderRadius.circular
        m = re.search(r'bl\s+#0x[0-9a-f]+\s+; \[package:[^\]]+\] ([A-Za-z:_]+)$', line.strip())
        if m:
            name = m.group(1)
            if any(k in name for k in ('withOpacity', 'circular', 'only', 'all', 'symmetric', 'fromRGBO')):
                events.append(('f', None, name))
            continue
    return events

IND = {'W': '  ', 'C': '    · ', 'T': '    · text ', 'S': '    · style ', 'K': '    · ', 'f': '      ~ ', 'c': '      ∙ '}

NOISE = {'AllocateContext', 'AllocateArray', 'AllocateGrowableArray', 'AllocateClosure',
         'AllocateDouble', 'InitLateFinalStaticField', 'StackOverflowSharedWithoutFPURegs',
         'inline_Allocate_Double', '_growToNextCapacity', 'AllocateRawArray',
         'AllocateContextStub', 'AllocateTypedData', 'AllocateSmallObject',
         'NullCastErrorSharedWithoutFPURegs', 'NullCastErrorSharedWithFPURegs',
         'RangeErrorSharedWithoutFPURegs', 'RangeErrorSharedWithFPURegs',
         'InitLateFinalInstanceField'}

CONSTS = {'Radius', 'BorderRadius', 'Color', 'BoxShadow', 'BoxDecoration', 'EdgeInsets',
          'TextStyle', 'Size', 'Offset', 'Tween', 'CurveTween', 'TweenSequenceItem',
          'TweenSequence', 'AnimationController', 'CurvedAnimation', 'Widget'}

def main():
    path = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 400
    evs = blueprint(path)
    print('// blueprint:', path)
    wcount = 0
    for kind, name, extra in evs[:limit]:
        if kind == 'W':
            wcount += 1
            label = name or extra or '?'
            tail = f'  {extra}' if (name and extra and not extra.startswith(name)) else ''
            print(f'[{wcount}] {label}{tail}')
        else:
            print(IND.get(kind, '    ') + (extra or name or ''))
    print(f'\n// total widget: {wcount}, events: {len(evs)}')

if __name__ == '__main__':
    main()
