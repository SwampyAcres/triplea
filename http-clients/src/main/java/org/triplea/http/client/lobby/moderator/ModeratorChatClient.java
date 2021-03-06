package org.triplea.http.client.lobby.moderator;

import java.net.URI;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.triplea.domain.data.ApiKey;
import org.triplea.domain.data.PlayerChatId;
import org.triplea.http.client.AuthenticationHeaders;
import org.triplea.http.client.HttpClient;

/** Provides access to moderator lobby commands, such as 'disconnect' player, and 'ban' player. */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModeratorChatClient {
  public static final String DISCONNECT_PLAYER_PATH = "/lobby/moderator/disconnect-player";
  public static final String BAN_PLAYER_PATH = "/lobby/moderator/ban-player";
  public static final String FETCH_PLAYER_INFORMATION = "/lobby/moderator/fetch-player-info";

  private AuthenticationHeaders authenticationHeaders;
  private ModeratorChatFeignClient moderatorLobbyFeignClient;

  public static ModeratorChatClient newClient(final URI lobbyUri, final ApiKey apiKey) {
    return new ModeratorChatClient(
        new AuthenticationHeaders(apiKey),
        new HttpClient<>(ModeratorChatFeignClient.class, lobbyUri).get());
  }

  public void banPlayer(final BanPlayerRequest banPlayerRequest) {
    moderatorLobbyFeignClient.banPlayer(authenticationHeaders.createHeaders(), banPlayerRequest);
  }

  public void disconnectPlayer(final PlayerChatId playerChatId) {
    moderatorLobbyFeignClient.disconnectPlayer(
        authenticationHeaders.createHeaders(), playerChatId.getValue());
  }

  public PlayerSummaryForModerator fetchPlayerInformation(final PlayerChatId playerChatId) {
    return moderatorLobbyFeignClient.fetchPlayerInformation(
        authenticationHeaders.createHeaders(), playerChatId.getValue());
  }
}
