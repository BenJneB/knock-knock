package be.ombres.conseil.game

/*
 * Types de salles (Chambres de cristal d'âme) et leur production, d'après les règles de la Défense de la capitale
 * et les captures : chaque joueur présent produit l'efficacité de la salle ; un bonus « 50% » l'augmente de moitié.
 * Vérifié sur les bandeaux : Ombres 2 intermédiaires = 2 × 3 × 4 = 24/s ; « 44 » 1 intermédiaire avec bonus
 * = 3 × 4 × 1,5 = 18/s ; Nexus (Zone sombre) 2 intermédiaires avec bonus = 36/s.
 */

/** Type de salle : nombre de lits et production d'un joueur au repos (/s). */
enum class RoomType(val label: String, val beds: Int, val perPlayer: Double) {
    BASIC("basique", 2, 2.0),
    INTERMEDIATE("intermédiaire", 3, 4.0),
    SUPREME("suprême", 4, 8.0),
    ;

    /** Production de la salle pleine, avec ou sans bonus. */
    fun full(buff: Boolean) = beds * perPlayer * (if (buff) BUFF else 1.0)

    companion object {
        /** Bonus d'efficacité « 50% » tiré au hasard chaque jour sur des salles basiques et intermédiaires. */
        const val BUFF = 1.5

        fun of(name: String?): RoomType? = entries.firstOrNull { it.name == name || it.label == name }
    }
}
