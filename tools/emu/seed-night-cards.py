#!/usr/bin/env python3
"""Load synthetic night-card fixtures into emulator-5554 only; never a real device."""
import argparse, copy, json, pathlib, subprocess
p=argparse.ArgumentParser();p.add_argument('--case',choices=['base','philosopher','imp','dreamer-poisoned','nightwatchman','nightwatchman-poisoned'],default='base');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parent
saved=json.loads((root/'fixtures/night-cards.json').read_text());g=saved['game']
if a.case=='philosopher': g['nightStepsDone']=['dusk']
if a.case=='imp':
 g['cycle']=2;g['nightStepsDone']=['dusk'];g['players']=[x for x in g['players'] if x['id'] in [1,2,4,6,7,8,9,10,11]]
if a.case=='dreamer-poisoned':
 g['players'][8]['reminders']=[dict(sourceId='widow',label='Poisoned')]
if a.case.startswith('nightwatchman'):
 g['players'][0]['characterId']='nightwatchman';g['script']['characterIds'].append('nightwatchman');g['nightStepsDone']=['dusk']
 if a.case.endswith('poisoned'):g['players'][0]['reminders']=[dict(sourceId='widow',label='Poisoned')]
adb='/opt/homebrew/share/android-commandlinetools/platform-tools/adb';pkg='com.clocktower.grimoire'
def run(*args,**kw):return subprocess.run([adb,'-s','emulator-5554',*args],check=True,**kw)
run('shell','am','force-stop',pkg)
run('shell','run-as',pkg,'mkdir','-p','files/datastore')
run('exec-in','run-as',pkg,'tee','files/datastore/grimoire.json',input=json.dumps(saved).encode(),stdout=subprocess.DEVNULL)
run('shell','am','start','-n',pkg+'/.MainActivity')
print('Seeded synthetic '+a.case+' on emulator-5554')
