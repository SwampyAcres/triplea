package org.triplea.modules.chat;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.triplea.domain.data.ChatParticipant;
import org.triplea.domain.data.UserName;

/** Keeps the current list of ChatParticipants and maps them to their websocket session. */
@Slf4j
@AllArgsConstructor
public class Chatters {
  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private final Map<String, ChatterSession> participants = new ConcurrentHashMap<>();

  public static Chatters build() {
    return new Chatters();
  }

  public Optional<ChatterSession> lookupPlayerBySession(final Session senderSession) {
    return Optional.ofNullable(participants.get(senderSession.getId()));
  }

  public void connectPlayer(final ChatterSession chatterSession) {
    participants.put(chatterSession.getSession().getId(), chatterSession);
  }

  public Collection<ChatParticipant> getChatters() {
    return participants.values().stream()
        .map(ChatterSession::getChatParticipant)
        .collect(Collectors.toList());
  }

  public Optional<UserName> playerLeft(final Session session) {
    return Optional.ofNullable(participants.remove(session.getId()))
        .map(ChatterSession::getChatParticipant)
        .map(ChatParticipant::getUserName);
  }

  public boolean isPlayerConnected(final UserName userName) {
    return participants.values().stream()
        .map(ChatterSession::getChatParticipant)
        .map(ChatParticipant::getUserName)
        .anyMatch(userName::equals);
  }

  public Collection<Session> fetchOpenSessions() {
    return participants.values().stream()
        .map(ChatterSession::getSession)
        .filter(Session::isOpen)
        .collect(Collectors.toSet());
  }

  /**
   * Disconnects all sessions belonging to a given player identified by name. A disconnected session
   * is closed, the closure will trigger a notification on the client of the disconnected player.
   *
   * @param userName The name of the player whose sessions will be disconnected.
   * @param disconnectMessage Message that will be displayed to the disconnected player.
   * @return True if any sessions were disconnected, false if none (indicating player was no longer
   *     in chat).
   */
  public boolean disconnectPlayerSessions(final UserName userName, final String disconnectMessage) {
    final Set<Session> sessions =
        participants.values().stream()
            .filter(
                chatterSession ->
                    chatterSession.getChatParticipant().getUserName().equals(userName))
            .map(ChatterSession::getSession)
            .collect(Collectors.toSet());

    // Do the session disconnects as a second step after gathering sessions to be disconnected.
    // This is to avoid concurrent modification of sessions.
    sessions.forEach(
        session -> {
          try {
            session.close(
                new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, disconnectMessage));
          } catch (final IOException e) {
            log.warn(
                "While closing session, "
                    + "session close threw an exception, session is left open? {}",
                session.isOpen(),
                e);
          }
        });
    return !sessions.isEmpty();
  }
}
