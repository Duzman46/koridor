package com.duzman46.gridbound.core

object Constants {
    object Board {
        const val SIZE = 9
        const val WALL_GRID_SIZE = SIZE - 1
        const val START_COLUMN = SIZE / 2
        const val PLAYER_ONE_START_ROW = SIZE - 1
        const val PLAYER_TWO_START_ROW = 0
        const val PLAYER_ONE_GOAL_ROW = 0
        const val PLAYER_TWO_GOAL_ROW = SIZE - 1
        const val STARTING_WALLS = 10
        const val MAX_PLACED_WALLS = STARTING_WALLS * 2
        const val TILE_CORNER_RADIUS_RATIO = 0.16f
        const val GAP_RATIO = 0.24f
        const val WALL_THICKNESS_RATIO = 0.58f
        const val PAWN_RADIUS_RATIO = 0.31f
    }

    object Ai {
        const val EASY_WALL_PROBABILITY = 0.22
        const val HARD_MAX_DEPTH = 3
        const val HARD_TIME_BUDGET_MILLIS = 850L
        const val HARD_MAX_WALL_CANDIDATES = 10
        const val TERMINAL_SCORE = 100_000
        const val OWN_DISTANCE_WEIGHT = 18
        const val OPPONENT_DISTANCE_WEIGHT = 20
        const val WALL_COUNT_WEIGHT = 3
        const val IMMEDIATE_THREAT_WEIGHT = 2_000
        const val MEDIUM_WALL_THRESHOLD = 2
    }

    object Animation {
        const val SPLASH_DURATION_MILLIS = 850L
        const val PAWN_DURATION_MILLIS = 260
        const val WALL_DURATION_MILLIS = 320
        const val INVALID_PREVIEW_MILLIS = 650L
        const val CONFETTI_PARTICLE_COUNT = 72
        const val CONFETTI_CYCLE_MILLIS = 2_800
    }

    object Audio {
        const val VOLUME_PERCENT = 72
        const val MOVE_DURATION_MILLIS = 90
        const val WALL_DURATION_MILLIS = 120
        const val ERROR_DURATION_MILLIS = 150
        const val VICTORY_DURATION_MILLIS = 420
    }

    object Ui {
        const val TABLET_BREAKPOINT_DP = 700
        const val CONTENT_MAX_WIDTH_DP = 1_200
        const val BOARD_MAX_SIZE_DP = 720
        const val DEFAULT_PADDING_DP = 16
        const val SMALL_PADDING_DP = 8
        const val LARGE_PADDING_DP = 24
    }

    object Data {
        const val STATE_FLOW_STOP_TIMEOUT_MILLIS = 5_000L
        const val SETTINGS_FILE_NAME = "gridbound_preferences"
        const val DEFAULT_VERSION_NAME = "0.3.0"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_TOTAL_GAMES = "total_games"
        const val KEY_TOTAL_WINS = "total_wins"
        const val KEY_TOTAL_LOSSES = "total_losses"
        const val KEY_LOCAL_GAMES = "local_games"
        const val KEY_TOTAL_TURNS = "total_turns"
        const val KEY_EASY_WINS = "easy_wins"
        const val KEY_MEDIUM_WINS = "medium_wins"
        const val KEY_HARD_WINS = "hard_wins"
        const val KEY_EASY_LOSSES = "easy_losses"
        const val KEY_MEDIUM_LOSSES = "medium_losses"
        const val KEY_HARD_LOSSES = "hard_losses"
    }

    object Online {
        const val ROOM_CODE_LENGTH = 6
        const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val MAX_ROOM_CREATE_ATTEMPTS = 8
        const val ROOMS_PATH = "rooms"
        const val ROOM_EXPIRY_MILLIS = 86_400_000L
    }
}
