class ColourScheme:
    def __init__(self, tp, tn, fp, fn_wronglocation, fn_missing):
        self.tp = tp
        self.tn = tn
        self.fp = fp
        self.fn_wronglocation = fn_wronglocation
        self.fn_missing = fn_missing

CSCHEME1 = ColourScheme('#0571b0', '#92c5de', '#f7f7f7', '#f4a582', '#ca0020')
CSCHEME2 = ColourScheme('forestgreen', 'mediumseagreen', 'chocolate', 'gold', 'firebrick')
