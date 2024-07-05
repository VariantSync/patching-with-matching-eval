package org.variantsync.evaluation.cherries


class CherryPick(
    val id: Int, val cherryCommit: String, val cherryParentCommit: String,
    val targetCommit: String, val expectedResultCommit: String,
)