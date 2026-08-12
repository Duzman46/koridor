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

        /**
         * Width of the channel between two tiles, as a fraction of a tile.
         *
         * Measured off the board render rather than chosen — see [FRAME_RATIO].
         */
        const val GAP_RATIO = 0.15590f

        /**
         * A placed wall's thickness, as a fraction of the channel it drops into.
         *
         * Above 1 on purpose. It used to be below 1 so the empty slot stayed visible either
         * side of the piece, which the drawn board needed because a drawn channel was only
         * ever a darker rectangle. The rendered board has real grooves with lit walls and gold
         * studs, so the slot is visible whether a wall is in it or not — and a wall that fits
         * *inside* the groove reads as paint in a line rather than as a piece. A real one is
         * thicker than the slot and stands proud of it, which is what this is.
         */
        const val WALL_THICKNESS_RATIO = 1.91f

        /**
         * The frame around the grid, as a fraction of the whole board.
         *
         * This and [GAP_RATIO] are not design choices — they are measurements of
         * `reference/tahta.png`, printed by `docs/store/board.py` and copied here. The renderer
         * draws that render as the board's face and then puts pawns and walls on top of it, so
         * the two lattices have to be the same lattice. Change the artwork, re-run the script,
         * copy both numbers back; do not tune either by eye.
         */
        const val FRAME_RATIO = 0.04191f
        const val PAWN_RADIUS_RATIO = 0.40f

        /**
         * How much of a tile's width a pawn's base covers.
         *
         * Width and not height, because the pieces are photographed from above: what says
         * whether a piece suits a square is how much of the square its base sits on, and the
         * height then follows from the sprite's own proportions. Sizing by height was right for
         * the tall ivory piece these replaced and wrong for them — it left a base crowding the
         * tile it stood on and a piece towering over the two beside it.
         *
         * Kept low enough that the piece clears the square on **all four** sides, which is a
         * tighter bound than merely fitting. At 0.66 the piece was already centred to within a
         * pixel — measured on the handset against the board's own studs — and still read as off,
         * because it filled the square vertically while leaving a gap either side, and an eye
         * takes that mismatch for misalignment rather than for proportion. At 0.56 the sprite is
         * about nine tenths of a tile tall and a little over half of it wide, so the margin is
         * even the whole way round and the piece sits in its square instead of straining at it.
         */
        const val PAWN_WIDTH_RATIO = 0.56f

        /**
         * How far down a pawn sprite the bottom rim of its base sits, as a fraction of its
         * height. Measured by `docs/store/pieces.py`, which prints it for both pieces.
         *
         * The sprite itself is **centred** on its square and this does not place it. The board
         * is drawn straight down — square tiles, circular studs, no perspective anywhere in it —
         * so there is no direction for a piece to lean into, and a piece stood up from its base
         * put its head in the square above and left its own square empty underneath. That is
         * what "not quite centred" looked like on the handset.
         *
         * What this still places is the contact shadow, which has to fall under the base rather
         * than under the middle of the picture. Finding it took two wrong answers: the base is a
         * cylinder, so the silhouette holds its full width from the top rim down to the bottom
         * one, and below the bottom rim there is still picture — the front of the ellipse curving
         * away. The top rim alone is a third of a base too high, the sprite's last row is too
         * low, and this is the rim between them.
         */
        const val PAWN_BASE_ANCHOR = 0.750f
    }

    object Ai {
        // ---- EASY / MEDIUM ----
        const val EASY_WALL_PROBABILITY = 0.22
        const val MEDIUM_MAX_WALL_CANDIDATES = 20
        const val MEDIUM_WALL_THRESHOLD = 2
        const val OWN_DISTANCE_WEIGHT = 18
        const val OPPONENT_DISTANCE_WEIGHT = 20

        // ---- shared search engine (HARD and EXPERT) ----
        const val SEARCH_WIN_SCORE = 1_000_000

        /**
         * A race the opponent provably cannot interfere with. Below a real mate, above any
         * heuristic.
         */
        const val SEARCH_PROVEN_WIN_SCORE = 900_000
        const val SEARCH_MATE_THRESHOLD = 800_000
        const val SEARCH_EVAL_CLAMP = 60_000
        const val SEARCH_MAX_PLY = 32

        /**
         * Poll the clock every 1024 nodes: ~9 ms of overshoot against a 1.2 s ceiling, and no
         * syscall in the inner loop.
         */
        const val SEARCH_NODE_POLL_MASK = 1_023
        const val SEARCH_ZOBRIST_SEED = 0x51D00DL

        // ---- evaluation, measured in plies-to-goal ----
        /** One ply of race advantage. One full step of shortest path is therefore 200. */
        const val RACE_PLY_VALUE = 100
        const val RACE_LINEAR_PLIES = 8
        const val RACE_TAPER_DIVISOR = 2

        /**
         * A wall in hand, ~0.35 of a step: enough that a one-step wall is refused, not enough
         * to hoard.
         */
        const val WALL_BASE_VALUE = 70

        /**
         * Wall difference beats wall count in the middlegame — holding more means getting the
         * last word.
         */
        const val WALL_SURPLUS_VALUE = 90
        const val WALL_SURPLUS_CAP = 3
        const val WALL_LAST_VALUE = 40

        /** Walls are worth less against a pawn that is nearly home, not merely "later". */
        const val WALL_RELEVANCE_DISTANCE = 8
        const val CENTRE_VALUE = 12
        const val CENTRE_RELEVANCE_DISTANCE = 6

        /**
         * A pawn with one progress direction is in a corridor; one wall then costs it many
         * steps.
         */
        const val FREEDOM_VALUE = 14

        /**
         * Two full steps of slack, which absorbs up to two tempi lost to pawn contact at the
         * meeting point.
         */
        const val PROVEN_RACE_MARGIN = 5
        const val RUN_RACE_MARGIN = 3

        // ---- ordering ----
        const val ORDER_WINNING_MOVE = 8_000_000
        const val ORDER_TT_MOVE = 4_000_000
        const val ORDER_KILLER_PRIMARY = 3_000_000
        const val ORDER_KILLER_SECONDARY = 2_900_000
        const val ORDER_PAWN_BASE = 1_000_000
        const val ORDER_PAWN_DISTANCE_STEP = 1_000
        const val ORDER_JUMP_BONUS = 5_000
        const val ORDER_WALL_BASE = 100_000
        const val ORDER_HISTORY_CAP = 900

        // ---- wall candidate generation ----
        const val WALL_PATH_EDGES_OPPONENT = 5
        const val WALL_PATH_EDGES_OWN = 3

        /**
         * Above this ply a candidate is ordered by a static key: two BFS per candidate is
         * unaffordable deeper.
         */
        const val WALL_SCORED_MAX_PLY = 2
        const val STATIC_KEY_OPPONENT_PATH = 400
        const val STATIC_KEY_TOUCHES_WALL = 200
        const val STATIC_KEY_NEAR_OPPONENT = 100
        const val STATIC_KEY_OWN_PATH = 150

        // ---- late move reductions ----
        const val LMR_MIN_DEPTH = 3
        const val LMR_MIN_MOVE_INDEX = 4
        const val LMR_SAFE_OPPONENT_DISTANCE = 2

        // ---- EXPERT budget ----
        /**
         * Past this point a new iteration cannot finish; ~1 s of perceived latency keeps the
         * opponent responsive.
         */
        const val EXPERT_SOFT_BUDGET_MILLIS = 700L

        /**
         * The tail: cold JIT on the first move, and a low-end handset three times slower than
         * a mid-range one.
         */
        const val EXPERT_HARD_BUDGET_MILLIS = 1_200L
        const val EXPERT_MAX_DEPTH = 20
        const val EXPERT_ROOT_WALL_CANDIDATES = 16
        const val EXPERT_SHALLOW_WALL_CANDIDATES = 10
        const val EXPERT_DEEP_WALL_CANDIDATES = 6
        const val EXPERT_TT_SIZE_LOG2 = 16

        // ---- HARD budget: same engine, shallower ----
        const val HARD_SOFT_BUDGET_MILLIS = 200L
        const val HARD_HARD_BUDGET_MILLIS = 350L
        const val HARD_MAX_DEPTH = 4
        const val HARD_ROOT_WALL_CANDIDATES = 8
        const val HARD_SHALLOW_WALL_CANDIDATES = 6
        const val HARD_DEEP_WALL_CANDIDATES = 4
        const val HARD_TT_SIZE_LOG2 = 14

        // ---- adaptive spend ----
        const val STABLE_ITERATIONS_TO_STOP = 3
        const val STABLE_SCORE_WINDOW = 50

        /** A reply that lands in 20 ms reads as careless from something labelled "Uzman". */
        const val MIN_THINK_MILLIS = 150L
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
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
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
        const val KEY_EXPERT_WINS = "expert_wins"
        const val KEY_EASY_LOSSES = "easy_losses"
        const val KEY_MEDIUM_LOSSES = "medium_losses"
        const val KEY_HARD_LOSSES = "hard_losses"
        const val KEY_EXPERT_LOSSES = "expert_losses"

        // What the achievements needed and the old counters could not answer.
        //
        // An online match was being filed as a medium bot match, because MEDIUM is the difficulty
        // the online route carries in its arguments and nothing downstream asked what mode it
        // was. So "beat the machine on medium" was earnable by a player who had never met the
        // machine. Online has counters of its own now, and the difficulty tallies belong to the
        // bot alone.
        const val KEY_ONLINE_GAMES = "online_games"
        const val KEY_ONLINE_WINS = "online_wins"
        const val KEY_CURRENT_STREAK = "current_streak"
        const val KEY_BEST_STREAK = "best_streak"

        /** Turns in the shortest match this player has won, and 0 when they have won none. */
        const val KEY_FASTEST_WIN_TURNS = "fastest_win_turns"

        /**
         * Which badges the player has already been told about.
         *
         * Absent, rather than empty, before the app has ever looked — and the difference is the
         * whole point. A badge is earned by the statistics, so an update that adds badges awards
         * them retroactively; absent means "this player predates the shelf", and everything they
         * already hold is marked seen without a word. Empty means a genuinely new player, for
         * whom the first badge is news.
         */
        const val KEY_SEEN_ACHIEVEMENTS = "seen_achievements"

        /**
         * Whose record the counters above are.
         *
         * They are device-local and were once meant to be exactly that. They are not: a player
         * who signed out and came back as a guest found the previous account's achievements and
         * statistics waiting for them, because nothing in the store said who had earned them.
         */
        const val KEY_STATISTICS_OWNER = "statistics_owner"

        /** When the app was last opened, and when it last said anything while it was closed. */
        const val KEY_LAST_OPENED = "last_opened_at"
        const val KEY_LAST_NUDGED = "last_nudged_at"
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
         * How long the browser will show a spinner before admitting it has no answer.
         *
         * The list is a live listener now rather than a poll, and a listener that cannot reach
         * the database does not fail — it simply never fires. Without this the indicator spins
         * for as long as the player sits there: an animation running at sixty frames a second
         * to say nothing, on a screen that could have shown the empty state instead.
         *
         * Long enough to cover a slow first connection, short enough that nobody watches it.
         */
        const val ROOM_BROWSER_FIRST_ANSWER_MILLIS = 6_000L

        /**
         * How far this handset's clock is from the server's, in milliseconds.
         *
         * Firebase keeps it up to date over the connection the app already holds open, so
         * reading it costs no round trip and answers even while offline.
         */
        const val SERVER_TIME_OFFSET_PATH = ".info/serverTimeOffset"

        /**
         * Whether the database connection is up, as Firebase itself reports it.
         *
         * A local read on a node the client maintains, not a request — so the waiting panel can
         * say whether the connection is good without spending anything to find out, and say it
         * truthfully rather than assuming.
         */
        const val CONNECTED_PATH = ".info/connected"

        /**
         * How much of the phone's storage the database may keep for itself.
         *
         * The library defaults to ten megabytes and never asks. That is a great deal of room
         * for a game whose whole state is a nine-by-nine board and a list of walls, and it is
         * what a player sees when the app's size keeps climbing long after the download
         * finished: every room ever browsed and every match ever played, held until eviction
         * gets round to it.
         *
         * Two megabytes holds a live match, the rooms on screen and the profiles behind them
         * several times over.
         */
        const val PERSISTENCE_CACHE_BYTES = 2L * 1024 * 1024

        /**
         * The wait the panel quotes while matchmaking.
         *
         * A stated typical figure, not a measurement: nothing in the app knows how many people
         * are queuing right now, so this cannot be computed from anything. It is here because a
         * blank space next to a running clock reads as "this could take forever", and a range
         * that is usually right is kinder than no answer. Revisit it if the queue ever reports
         * its own depth.
         */
        const val QUEUE_TYPICAL_WAIT_LOW_SECONDS = 10
        const val QUEUE_TYPICAL_WAIT_HIGH_SECONDS = 25

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
         *
         * For handing over to another account, and nothing else. A wait that is only letting a
         * flow catch up with a credential already in hand is a different question with a very
         * different answer; see [SESSION_CATCHUP_TIMEOUT_MILLIS].
         */
        const val IDENTITY_SETTLE_TIMEOUT_MILLIS = 15_000L

        /**
         * How long the session flow is given to catch up with an identity that has already
         * landed, before the work waiting on it goes ahead regardless.
         *
         * Deliberately nothing like [IDENTITY_SETTLE_TIMEOUT_MILLIS], because it is not waiting
         * for the same thing. Nothing is in flight: the credential exists and has been read from
         * the very source the session is built on, so all that is outstanding is a new ID token
         * reaching a listener and a database connection inside this process. That is a
         * propagation measured in milliseconds, and a second is already an order of magnitude of
         * slack for a cold device.
         *
         * The bound is short for the player's sake rather than the flow's. The one caller on a
         * critical path is the username gate, which by design has no back arrow, swallows system
         * back and blocks its own submit for the duration — so every second of this is a second
         * somebody is held behind a spinner on a screen with no way out. Running out is not a
         * failure either: it means the write goes ahead against the identity that was read
         * directly, which is the identity the wait was hoping to be shown.
         */
        const val SESSION_CATCHUP_TIMEOUT_MILLIS = 1_000L
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

        /**
         * contentReports/{subjectId}/{reporterId} — what one player says about another's
         * username or room name.
         *
         * Write-only from a client's point of view: nobody may read it, so a report cannot be
         * seen, answered or deleted by the account it names. The operator reads them out of
         * the database console.
         */
        const val CONTENT_REPORTS_PATH = "contentReports"

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
