# Type-safe navigation looks up an enum route argument's class by its fully-qualified name at
# runtime (GameSettingsRoute / GameStatsRoute carry a GameId), so R8 must keep that enum's name.
# Without this the minified build crashes as soon as the nav graph is built.
-keepnames class com.cardgames.core.domain.game.GameId
