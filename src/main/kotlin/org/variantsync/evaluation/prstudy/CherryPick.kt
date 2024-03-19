package org.variantsync.evaluation.prstudy


class CherryPick(
    val id: Int, val cherryCommit: String, val cherryParentCommit: String,
    val targetCommit: String, val expectedResultCommit: String,
)