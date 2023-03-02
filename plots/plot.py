import matplotlib.pyplot as plt
from matplotlib.sankey import Sankey
import os
import numpy


# OUTPUT_FORMAT = ".png"
OUTPUT_FORMAT = ".pdf"
DPI = 300


def toThousandsFormattedString(intval):
    return f"{intval:,}"


def percentageToAmount(percentage, total):
    # Is round correct here?
    return round((total * float(percentage)) / 100.0)


def labelPrecentageAndAmount(total):
    return lambda percentage: str(round(percentage,2)) + "%\n" + toThousandsFormattedString(percentageToAmount(percentage, total)) + " patches"
def labelPrecentage():
    return lambda percentage: '{:.1f}%'.format(percentage)


def rq1_barchart(experiment, outputPath):
    # colourscheme = 'teal', 'orange'
    commit_success = experiment.commitSuccess
    commit_failed = experiment.getNumCommitFailures()
    file_success = experiment.fileSuccess
    file_failed = experiment.getNumFilePatchFailures()
    line_success = experiment.lineSuccess
    line_failed = experiment.getNumLinePatchFailures()

    labels = ['(a) commit-sized', '(b) file-sized', '(c) line-sized']
    success_vals = [commit_success, file_success, line_success]
    failed_vals = [commit_failed, file_failed, line_failed]
    sum_vals = numpy.sum([success_vals, failed_vals], axis=0)
    # print(numpy.sum(fvals))

    # normalize values
    # def normalize(vals, total):
    #     return list(map(lambda x: float(x)/float(total), vals))
    # nvals = normalize(nvals, ntotal)
    # fvals = normalize(fvals, ftotal)

    _scalefactor = 1
    x = numpy.arange(_scalefactor * len(labels), step=_scalefactor)
    # print(x)
    widthOfBars = 0.2

    fig, ax = plt.subplots()
    success_percentage = numpy.divide(success_vals, sum_vals)
    failed_percentage = numpy.divide(failed_vals, sum_vals)
    success_rects = ax.bar(x - 0.05 - widthOfBars / 2, 100*success_percentage, widthOfBars, label='applicable', alpha=1)
    failed_rects = ax.bar(x + 0.05 + widthOfBars / 2, 100*failed_percentage, widthOfBars, label='failed', alpha=1)

    def label_values(percentage, absolute, offset):
        for i, v in enumerate(percentage):
            v = 100*v
            label = "{:2.1f}".format(v)
            label += "%"
            ax.text(i+offset+0.1, v + 5, label)
            label = "({:,.0f})".format(absolute[i])
            ax.text(i+offset, v + 1, label)

    label_values(success_percentage, success_vals, -0.4)
    label_values(failed_percentage, failed_vals, -0.05)

    # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Percentage of patches')
    ax.set_ylim([0, 100])
    ax.set_xlim([-0.5, 2.6])
    # ax.set_yscale('log')
    # ax.set_title('Scores by group and gender')
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.legend(loc=1)

    ax.bar_label(success_rects,
                 labels=map(lambda x: "", success_vals),
                 padding=3)
    ax.bar_label(failed_rects,
                 labels=map(lambda x: "", failed_vals),
                 padding=3)

    fig.tight_layout()

    plt.savefig(outputPath, dpi=DPI, bbox_inches='tight')


def correctness_barchart(experiment, outputPath, colourscheme):
    # colourscheme = 'forestgreen', 'darkorange'
    colors = [colourscheme.tp, colourscheme.fp, colourscheme.fn_wronglocation, colourscheme.tn, colourscheme.fn_missing]

    labels = ['correct\n(TP)', 'invalid\n(FP)', 'wrong location\n(FN)', 'not required\n(TN)', 'missing\n(FN)']
    success_vals = [experiment.tp, experiment.fp, experiment.wrongLocation]
    failed_vals = [experiment.tn, experiment.fn - experiment.wrongLocation]

    _scalefactor = 1
    s = numpy.arange(_scalefactor * len(success_vals), step=_scalefactor)
    start = _scalefactor * len(success_vals) + 0.3
    stop = start + _scalefactor * len(failed_vals)
    f = numpy.arange(start=start, stop=stop, step=_scalefactor)
    widthOfBars = 0.35

    fig, ax = plt.subplots()
    success_percentage = 100 * numpy.divide(success_vals, numpy.sum(success_vals))
    failed_percentage = 100 * numpy.divide(failed_vals, numpy.sum(failed_vals))
    for i,x in enumerate(s):
        success_rects = ax.bar(x, success_percentage[i], widthOfBars, color=colors[i], edgecolor='black')
    for i,x in enumerate(f):
        failed_rects = ax.bar(x, failed_percentage[i], widthOfBars, color=colors[i+3], edgecolor='black')
    ax.vlines(2.65, ymin=0, ymax=105, color='black')

    def label_values(percentage, absolute, offset):
        for i, v in enumerate(percentage):
            v = v
            label = "{:2.1f}".format(v)
            label += "%"
            ax.text(i+offset+0.3, v + 5, label)
            label = "({:,.0f})".format(absolute[i])
            ax.text(i+offset, v + 1, label)

    label_values(success_percentage, success_vals, -0.42)
    label_values(failed_percentage, failed_vals, 2.8)

    props = dict(boxstyle='round', alpha=0.2)
    ax.text(1.9, 95, " applied\npatches", bbox=props)
    props = dict(boxstyle='round', alpha=0.2)
    ax.text(4.5, 95, "  failed\npatches", bbox=props)

    # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Percentage of patches')
    ax.set_ylim([0, 105])
    ax.set_xlim([-0.5, 5.2])
    # ax.set_yscale('log')
    # ax.set_title('Scores by group and gender')
    ax.set_xticks(numpy.append(s, f))
    ax.set_xticklabels(labels)

    # ax.bar_label(success_rects,
    #              labels=map(lambda x: "", success_vals),
    #              padding=3)
    # ax.bar_label(failed_rects,
    #              labels=map(lambda x: "", failed_vals),
    #              padding=3)

    fig.tight_layout()

    plt.savefig(outputPath, dpi=DPI, bbox_inches='tight')



def rq2_innerlabelfix1(autotexts):
    autotexts[2]._x = autotexts[2]._x + 0.07
    autotexts[1]._x = autotexts[1]._x + 0.01
    autotexts[2]._y = autotexts[2]._y + 0.2
    # autotexts[1]._y = autotexts[1]._y - 0.08
    autotexts[0].set_color('white')
def rq2_innerlabelfix2(autotexts):
    autotexts[1]._x = autotexts[1]._x + 0.05
    autotexts[1]._y = autotexts[1]._y + 0.2
    autotexts[1].set_color('white')

def rq3_innerlabelfix1(autotexts):
    autotexts[1]._y = autotexts[1]._y - 0.1
def rq3_outerlabelfix1(texts):
    texts[0]._y = texts[0]._y + 0.09
    texts[1]._y = texts[1]._y - 0.09

def rq3_granularity_innerlabelfix(autotexts):
    autotexts[0]._y = autotexts[0]._y + 0.05
    autotexts[1]._y = autotexts[1]._y - 0.1
def rq3_granularity_outerlabelfix(texts):
    texts[2]._y = texts[2]._y + 0.08

def rq3_barchart(experiment, colourscheme, outDir):
    n = experiment.normal
    f = experiment.filtered

    labels = ['TP', 'FP', 'TN', 'FN']
    nvals = [n.tp, n.fp, n.tn, n.fn]
    ntotal = numpy.sum(nvals)
    fvals = [f.tp, f.fp, f.tn, f.fn]
    ftotal = numpy.sum(fvals)
    # print(numpy.sum(fvals))

    # normalize values
    def normalize(vals, total):
        return list(map(lambda x: float(x)/float(total), vals))
    nvals = numpy.multiply(normalize(nvals, ntotal), 100)
    fvals = numpy.multiply(normalize(fvals, ftotal), 100)

    _scalefactor = 3
    start=2
    stop=start+_scalefactor * len(labels)
    x = numpy.arange(start=start, stop=stop, step=_scalefactor)
    # print(x)
    widthOfBars = 0.5

    fig, ax = plt.subplots()
    nrects = ax.bar(x - 0.05 - widthOfBars/2, nvals, widthOfBars, label='without domain knowledge')
    frects = ax.bar(x + 0.05 + widthOfBars/2, fvals, widthOfBars, label='with domain knowledge')
    
    # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Percentage of Patches')
    ax.set_ylim([0, 100])
    ax.set_xlim([-0.1, stop + 0.75 - _scalefactor/2])
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.legend()

    def label_values(percentage, offset_x, offset_y):
        for i, v in enumerate(percentage):
            label = "{:2.1f}".format(v)
            label += "%"
            ax.text(i*_scalefactor + start + offset_x, v + offset_y, label)

    label_values(nvals, -1.9, 1)
    label_values(fvals, 0.5, 1)

    ax.bar_label(nrects,
        labels=map(lambda x: "", nvals),
        padding=3)
    ax.bar_label(frects,
        labels=map(lambda x: "", fvals),
        padding=3)

    fig.tight_layout()

    plt.savefig(os.path.join(outDir, "rq3_domain_knowledge" + OUTPUT_FORMAT), dpi=DPI, bbox_inches='tight')


def sankey(patchstrategy):
    # first row
    numPatches = float(patchstrategy.line)

    # second row
    numApplicable = float(patchstrategy.lineSuccess) # from numPatches
    numFailed = float(patchstrategy.getNumLinePatchFailures()) # from numPatches
    # numApplicable = numApplicable / numPatches
    # numFailed = numFailed / numPatches
    # numPatches = 1.0

    # third row
    numCorrect = patchstrategy.tp # from numApplicable
    numWrongLocation = patchstrategy.wrongLocation # from numApplicable
    numInvalid = patchstrategy.fp # from numApplicable
    numNotRequired = patchstrategy.tn # from numFailed
    numMissing = patchstrategy.fn - numWrongLocation # from numFailed

    print("numApplicable", numApplicable)
    print("numFailed", numFailed)
    print()
    print("numCorrect", numCorrect)
    print("numWrongLocation", numWrongLocation)
    print("numInvalid", numInvalid)
    print("numCorrect + numWrongLocation + numInvalid", numCorrect + numWrongLocation + numInvalid)
    print("numNotRequired", numNotRequired)
    print("numMissing", numMissing)
    print("numNotRequired + numMissing", numNotRequired + numMissing)
    print()

    # fourth row
    tp = patchstrategy.tp # from numCorrect
    fp = patchstrategy.fp # from numInvalid
    tn = patchstrategy.tn # from numNotRequired
    fn = patchstrategy.fn # from numWrongLocation + numMissing

    ### plotting

    # fig = plt.figure()
    # ax = fig.add_subplot(1, 1, 1, xticks=[], yticks=[],
    #                     title="Flow Diagram of a Widget")

    sankey = Sankey(
        # ax=ax,
        unit='',
        scale=1.0 / numPatches,
        # offset=0.2,
        head_angle=150,
        # format='%.0f',
        format = '',
        shoulder = 0
        )

    flows1  = [numPatches, -numApplicable, -numFailed]
    labels1 = ['All Patches', 'Applicable', 'Failed']
    print(labels1)
    print(flows1)
    sankey.add(
        flows=flows1,
        labels=labels1,
        orientations=[0, 0, 0],
        # pathlengths=[float(numApplicable) / float(numPatches), float(numApplicable) / float(numFailed)]
        # , patchlabel="Widget\nA"  # Arguments to matplotlib.patches.PathPatch
        )

    flows2  = [numApplicable, -numCorrect, -numWrongLocation, numFailed, -numInvalid, -numNotRequired, -numMissing]
    labels2 = ['', 'Correct', 'Wrong Location', '', 'Invalid', 'Not Required', 'Missing']
    # print(labels2)
    # print(flows2)
    sankey.add(
        flows=flows2,
        labels=labels2,
        orientations=[0, 0, 0, 0, 0, 0, 0]
        , prior=0
        , connect=(1, 0)
    )

    diagrams = sankey.finish()
    # diagrams[0].texts[-1].set_color('r')
    # diagrams[0].text.set_fontweight('bold')
    plt.show()


def rq1(patchstrategy, outDir):
    print("RQ1")
    rq1_barchart(patchstrategy, os.path.join(outDir, "rq1_applicability" + OUTPUT_FORMAT))


def rq2(patchstrategy, colourscheme, outDir):
    print("RQ2")
    correctness_barchart(patchstrategy, os.path.join(outDir, "rq2_correctness" + OUTPUT_FORMAT), colourscheme)


def rq3(experiment, colourscheme, outDir):
    print("RQ3")
    rq3_barchart(experiment, colourscheme, outDir)
