package games.cardgames

/**
 * Ключ игры: строка, а не enum.
 *
 * Игры появляются по одной, и ключ переживает версию — он лежит в настройках
 * (какую игру открывали последней) и в имени файла недоигранной партии.
 * Enum здесь пришлось бы дописывать каждый раз и следить, чтобы старые
 * значения не поехали; строка этого не требует.
 */
const val GAME_DURAK = "durak"
const val GAME_THOUSAND = "thousand"
const val GAME_KOZEL = "kozel"
const val GAME_HUNDRED = "hundred"
