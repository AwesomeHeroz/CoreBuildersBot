package com.corebuilders.bot.web.auth;

import java.net.URI;

public interface DiscordOAuth {
    URI authorizationUri(String state);
    DiscordIdentity exchange(String authorizationCode);
}
