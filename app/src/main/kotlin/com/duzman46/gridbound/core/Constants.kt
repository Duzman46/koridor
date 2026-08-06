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

        /** Width of the channel between two tiles, as a fraction of a tile. */
        const val GAP_RATIO = 0.24f

        /** How much of that channel a placed wall fills. Below 1 so the slot stays visible. */
        const val WALL_THICKNESS_RATIO = 0.74f

        /** The frame around the grid, as a fraction of the whole board. */
        const val FRAME_RATIO = 0.022f
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

        /** How wide a column of settings or form fields is allowed to get on a tablet. */
        const val FORM_MAX_WIDTH_DP = 620
        const val BOARD_MAX_SIZE_DP = 720
        const val DEFAULT_PADDING_DP = 16
        const val SMALL_PADDING_DP = 8
        const val LARGE_PADDING_DP = 24
    }

    object Data {
        const val STATE_FLOW_STOP_TIMEOUT_MILLIS = 5_000L
        const val SETTINGS_FILE_NAME = "gridbound_preferences"
        const val DEFAULT_VERSION_NAME = "0.4.0"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME_MODE = "theme_mode"
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

        /** Excludes 0/O and 1/I so a code read aloud or retyped cannot be misheard. */
        const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val MAX_ROOM_CREATE_ATTEMPTS = 8
        const val ROOMS_PATH = "rooms"

        /** Password hashes, deliberately unreadable by any client. */
        const val ROOM_SECRETS_PATH = "roomSecrets"

        /** A room that is never played is swept away after this long. */
        const val ROOM_EXPIRY_MILLIS = 86_400_000L

        /** A room waiting for an opponent gives up sooner than a played one. */
        const val WAITING_ROOM_EXPIRY_MILLIS = 1_800_000L

        const val ROOM_NAME_MAX_LENGTH = 32
        const val ROOM_PASSWORD_MIN_LENGTH = 4

        /** How many public rooms the browser fetches at a time. */
        const val ROOM_BROWSER_PAGE_SIZE = 30

        /**
         * A player who drops out has this long to come back before the opponent may claim
         * the win. Short network blips must never cost a match.
         */
        const val DISCONNECT_GRACE_MILLIS = 45_000L

        /** How long quick match looks for an opponent before offering to host instead. */
        const val QUICK_MATCH_TIMEOUT_MILLIS = 20_000L
    }

    /** Realtime Database layout. Every path is mirrored by a rule in database.rules.json. */
    object Backend {
        /** Publicly readable player profile. Competitive fields are server-owned. */
        const val USERS_PATH = "users"

        /** Owner-only data such as the email address. Never exposed to other players. */
        const val USERS_PRIVATE_PATH = "usersPrivate"

        /** normalizedUsername -> userId. Guarantees case-insensitive uniqueness. */
        const val USERNAMES_PATH = "usernames"

        /** Append-only match reports, consumed by the rating Cloud Function. */
        const val MATCH_RESULTS_PATH = "matchResults"

        /** leaderboards/weekly/{weekKey}/{uid}, maintained by the rating function. */
        const val LEADERBOARDS_PATH = "leaderboards"
        const val WEEKLY_LEADERBOARD_PATH = "weekly"

        const val STARTING_RATING = 1_000
        const val MAX_USERNAME_ATTEMPTS = 6
    }

    object Billing {
        /** purchaseReceipts/{uid}/{tokenHash} — write-once, verified by a Cloud Function. */
        const val PURCHASE_RECEIPTS_PATH = "purchaseReceipts"
    }

    object Social {
        /** friendships/{ownerId}/{otherId} — stored under both players. */
        const val FRIENDSHIPS_PATH = "friendships"

        /** invites/{recipientId}/{senderId} — keyed by sender so invites cannot pile up. */
        const val INVITES_PATH = "invites"

        /** presence/{userId} — maintained with onDisconnect. */
        const val PRESENCE_PATH = "presence"

        const val INVITE_TTL_MILLIS = 600_000L
        const val MAX_FRIENDS = 200
    }

    object Leaderboard {
        const val PAGE_SIZE = 50

        /**
         * How far the exact-rank scan will go before reporting "N+". Bounds the work a
         * single "my rank" lookup can cost as the player base grows.
         */
        const val RANK_SCAN_LIMIT = 200
    }

    object Profile {
        const val DEFAULT_AVATAR_ID = "avatar_01"
        const val AVATAR_COUNT = 12
        const val MIN_PASSWORD_LENGTH = 8
        const val DISPLAY_NAME_MAX_LENGTH = 24
    }

    object Tutorial {
        const val KEY_COMPLETED = "tutorial_completed"
        const val HIGHLIGHT_PULSE_MILLIS = 1_100
    }

    object Session {
        /**
         * Set once the player chooses "play as guest". It lets local play and the tutorial
         * start with no network at all, and survives a failed anonymous sign-in.
         */
        const val KEY_GUEST_MODE_ACCEPTED = "guest_mode_accepted"
    }
}
