package org.variantsync.evaluation.cherries

class CherryDataset(
    val datasetName: String,
    val repositoryId: String,
    val language: String,
    var cherryPicks: MutableList<CherryPick>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CherryDataset

        if (datasetName != other.datasetName) return false
        if (repositoryId != other.repositoryId) return false
        if (language != other.language) return false

        return true
    }

    override fun hashCode(): Int {
        var result = datasetName.hashCode()
        result = 31 * result + repositoryId.hashCode()
        result = 31 * result + language.hashCode()
        return result
    }
}