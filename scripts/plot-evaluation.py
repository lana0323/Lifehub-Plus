"""Render a static figure from the recorded evaluation summary."""
from pathlib import Path
import json
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
root=Path(__file__).resolve().parent.parent/'docs/evaluation'
s=json.loads((root/'summary.json').read_text())
plt.rcParams.update({'font.family':'DejaVu Sans','font.size':11,'axes.spines.top':False,'axes.spines.right':False})
fig,(left,right)=plt.subplots(1,2,figsize=(13,5.2),gridspec_kw={'width_ratios':[1.15,1]})
fig.patch.set_facecolor('#f5f8f7')
items=[('Routing (all cases)',s['firstPass']['routingOverall']),('Required fields',s['firstPass']['requiredFieldMicro']),('Workflow entry (supported)',s['androidWorkflowEntrySupported']),('Clarification handling',s['androidClarifications'])]
left.set_facecolor('#f5f8f7');right.set_facecolor('#f5f8f7')
for i,(label,m) in enumerate(items):
 left.barh(i,100*m['rate'],color='#197469' if i<3 else '#c07150',height=.54)
 left.text(101,i,f"{m['passed']}/{m['total']}  {m['rate']:.1%}",va='center',fontsize=10)
left.set_yticks(range(4),[x[0] for x in items]);left.invert_yaxis();left.set_xlim(0,128);left.set_xticks([0,25,50,75,100]);left.set_xlabel('Percent');left.set_title('Primary outcomes',loc='left',fontweight='bold',pad=18);left.grid(axis='x',alpha=.15);left.set_axisbelow(True)
for i,(g,m) in enumerate(s['byWorkflow'].items()):
 r=m['androidWorkflowEntry'];right.barh(i,100*r['rate'],color='#237d70',height=.54);right.text(101,i,f"{r['passed']}/{r['total']}",va='center')
right.set_yticks(range(4),[x.title() for x in s['byWorkflow']]);right.invert_yaxis();right.set_xlim(0,120);right.set_xticks([0,25,50,75,100]);right.set_xlabel('Percent');right.set_title('Workflow entry by module',loc='left',fontweight='bold',pad=18);right.grid(axis='x',alpha=.15);right.set_axisbelow(True)
fig.suptitle('Lifehub Plus  /  Frozen evaluation v1',x=.035,ha='left',fontsize=21,fontweight='bold',color='#164d45')
fig.text(.035,.89,'120 cases · 60 English / 60 Chinese · Local Qwen3 4B · One first-pass run',color='#60726c')
fig.text(.035,.07,f"Latency P50 / P95: {s['firstPass']['latencyMs']['p50']/1000:.3f}s / {s['firstPass']['latencyMs']['p95']/1000:.3f}s     |     Reliability: {s['reliability']['passed']}/{s['reliability']['total']} passed",fontsize=12,color='#164d45')
fig.text(.035,.025,'Workflow entry uses recorded-response Android replay, not per-case database insertion. Internally authored holdout; see report for limitations.',fontsize=8.5,color='#60726c')
fig.subplots_adjust(left=.23,right=.965,top=.76,bottom=.23,wspace=.72)
fig.savefig(root/'overview.png',dpi=180,facecolor=fig.get_facecolor());plt.close(fig)
