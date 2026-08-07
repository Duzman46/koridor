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

        /** Long enough to carry several pips, so it reads as a countdown and not as a blip. */
        const val WARNING_DURATION_MILLIS = 700
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
        const val KEY_MATCH_MESSAGES_ENABLED = "match_messages_enabled"
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
         * How often the room browser reloads itself while the player is looking at it.
         *
         * Rooms appear and are taken within a minute or two, so a list left alone is stale
         * almost at once, and pulling to refresh is the player doing the app's job. Short
         * enough that a room opened while you are reading the list turns up before you have
         * lost interest, long enough that the whole cost is one indexed query of at most
         * thirty rows every fifteen seconds.
         */
        const val ROOM_BROWSER_REFRESH_MILLIS = 15_000L

        /** matchmaking/{uid} — who is waiting to be paired, and at what rating. */
        const val MATCHMAKING_PATH = "matchmaking"

        /**
         * How old a waiting-list entry may be before it is treated as abandoned. The
         * scheduled worker holds the same number and is what actually deletes them.
         *
         * A phone that closes cleanly takes its own entry with it, and one that dies has the
         * removal run for it by the onDisconnect handler the server holds. This is the
         * backstop for neither happening — a process killed while offline, say. Long enough
         * to survive a lift ride, short enough that nobody is paired against an app that
         * closed minutes ago and would never arrive. A player who is genuinely still waiting
         * simply takes a new place when this one is cleared.
         */
        const val MATCHMAKING_STALE_MILLIS = 300_000L

        /**
         * The tail of a turn that is called out — the clock turns red and a warning sounds.
         *
         * Long enough to finish a move already half decided, short enough that it is not
         * ringing for most of a thirty-second turn.
         */
        const val TURN_WARNING_MILLIS = 5_000L

        /**
         * A match nobody has moved in for this long is over: the room closes and the seat on
         * the clock loses it.
         *
         * This is what replaced "return to your match". A match left open indefinitely means
         * an opponent waiting for someone who is never coming back, and a lobby entry that
         * offers a way back in only postpones that. Ten minutes is far longer than any turn
         * anyone actually takes, so it can only catch a player who has genuinely walked away.
         */
        const val IDLE_FORFEIT_MILLIS = 600_000L

        /**
         * The shortest gap allowed between two messages from the same player.
         *
         * database.rules.json holds the same number and is what actually enforces it; this
         * copy only keeps the app from sending a write it already knows will be refused. A
         * closed vocabulary means nobody can say anything unpleasant, but fifty of anything a
         * second is unpleasant on its own.
         */
        const val CHAT_MIN_INTERVAL_MILLIS = 3_000L

        /**
         * How long a message stays on screen.
         *
         * Long enough to read across a board, short enough that a rival's last word is not
         * still sitting there ten moves later — which is what makes it something nobody has
         * to dismiss.
         */
        const val CHAT_VISIBLE_MILLIS = 7_000L
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

        /**
         * recentMatches/{uid} — the short match history a profile shows, written and capped
         * by the server and readable by any signed-in player.
         *
         * Deliberately not a child of the profile: the leaderboard reads fifty profiles in one
         * query and the session listens to one for as long as the app is open, and neither of
         * them wants ten match rows riding along on every read.
         */
        const val RECENT_MATCHES_PATH = "recentMatches"

        /** leaderboards/weekly/{weekKey}/{uid}, maintained by the rating function. */
        const val LEADERBOARDS_PATH = "leaderboards"
        const val WEEKLY_LEADERBOARD_PATH = "weekly"

        const val STARTING_RATING = 1_000
        const val MAX_USERNAME_ATTEMPTS = 6

        /**
         * How long a change of identity is given to reach the session every screen reads
         * before it counts as having failed.
         *
         * Generous, because the wait spans a sign-in and the database connection
         * re-authenticating behind it, and telling a player "that did not work" while it
         * quietly did is the worst answer available. Bounded, because the alternative to a
         * bound is a confirmation that never comes back.
         */
        const val IDENTITY_SETTLE_TIMEOUT_MILLIS = 15_000L
    }

    object Billing {
        /** purchaseReceipts/{uid}/{tokenHash} — write-once, verified by a Cloud Function. */
        const val PURCHASE_RECEIPTS_PATH = "purchaseReceipts"
    }

    object Social {
        /** friendships/{ownerId}/{otherId} — stored under both players. */
        const val FRIENDSHIPS_PATH = "friendships"

        /**
         * invites/{recipientId}/{senderId} — the live request channel, keyed by sender so one
         * person can never stack up more than one question. Each entry carries a kind, so an
         * invitation, a rematch and its refusal all reach a player who is listening in exactly
         * one place. The path keeps its original name because the data under it does.
         */
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

        /**
         * Set once the player has picked their own name. A profile is created with a
         * generated one like "player_jH7Go2" so play can start offline; this is what
         * distinguishes "never asked" from "asked and answered".
         */
        const val KEY_USERNAME_CHOSEN = "username_chosen"
    }
}
