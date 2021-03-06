package com.velocitypowered.api.proxy;

import com.velocitypowered.api.server.ServerInfo;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a Minecraft proxy server that follows the Velocity API.
 */
public interface ProxyServer {
    /**
     * Retrieves the player currently connected to this proxy by their Minecraft username.
     * @param username the username
     * @return an {@link Optional} with the player
     */
    Optional<Player> getPlayer(@Nonnull String username);

    /**
     * Retrieves the player currently connected to this proxy by their Minecraft UUID.
     * @param uuid the UUID
     * @return an {@link Optional} with the player
     */
    Optional<Player> getPlayer(@Nonnull UUID uuid);

    /**
     * Retrieves all players currently connected to this proxy. This call may or may not be a snapshot of all players
     * online.
     * @return the players online on this proxy
     */
    Collection<Player> getAllPlayers();

    /**
     * Retrieves a registered {@link ServerInfo} instance by its name.
     * @param name the name of the server
     * @return the server
     */
    Optional<ServerInfo> getServerInfo(@Nonnull String name);

    /**
     * Retrieves all {@link ServerInfo}s registered with this proxy.
     * @return the servers registered with this proxy
     */
    Collection<ServerInfo> getAllServers();

    /**
     * Registers a server with this proxy. A server with this name should not already exist.
     * @param server the server to register
     */
    void registerServer(@Nonnull ServerInfo server);

    /**
     * Unregisters this server from the proxy.
     * @param server the server to unregister
     */
    void unregisterServer(@Nonnull ServerInfo server);
}
