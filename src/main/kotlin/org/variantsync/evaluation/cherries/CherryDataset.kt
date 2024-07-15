package org.variantsync.evaluation.cherries

class CherryDataset(
    val datasetName: String,
    val repositoryId: String,
    val language: String,
    var cherryPicks: List<CherryPick>
)