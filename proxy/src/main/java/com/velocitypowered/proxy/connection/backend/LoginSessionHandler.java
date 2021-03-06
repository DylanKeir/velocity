package com.velocitypowered.proxy.connection.backend;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.config.IPForwardingMode;
import com.velocitypowered.proxy.connection.VelocityConstants;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.data.GameProfile;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.*;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPipeline;
import net.kyori.text.TextComponent;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LoginSessionHandler implements MinecraftSessionHandler {
    private final ServerConnection connection;
    private ScheduledFuture<?> forwardingCheckTask;

    public LoginSessionHandler(ServerConnection connection) {
        this.connection = connection;
    }

    @Override
    public void activated() {
        if (VelocityServer.getServer().getConfiguration().getIpForwardingMode() == IPForwardingMode.MODERN) {
            forwardingCheckTask = connection.getMinecraftConnection().getChannel().eventLoop().schedule(() -> {
                connection.getProxyPlayer().handleConnectionException(connection.getServerInfo(),
                        TextComponent.of("Your server did not send the forwarding request in time. Is it set up correctly?"));
            }, 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void handle(MinecraftPacket packet) {
        if (packet instanceof EncryptionRequest) {
            throw new IllegalStateException("Backend server is online-mode!");
        } else if (packet instanceof LoginPluginMessage) {
            LoginPluginMessage message = (LoginPluginMessage) packet;
            if (VelocityServer.getServer().getConfiguration().getIpForwardingMode() == IPForwardingMode.MODERN &&
                    message.getChannel().equals(VelocityConstants.VELOCITY_IP_FORWARDING_CHANNEL)) {
                LoginPluginResponse response = new LoginPluginResponse();
                response.setSuccess(true);
                response.setId(message.getId());
                response.setData(createForwardingData(connection.getProxyPlayer().getRemoteAddress().getHostString(),
                        connection.getProxyPlayer().getProfile()));
                connection.getMinecraftConnection().write(response);
                cancelForwardingCheck();

                ServerLogin login = new ServerLogin();
                login.setUsername(connection.getProxyPlayer().getUsername());
                connection.getMinecraftConnection().write(login);
            } else {
                // Don't understand
                LoginPluginResponse response = new LoginPluginResponse();
                response.setSuccess(false);
                response.setId(message.getId());
                response.setData(Unpooled.EMPTY_BUFFER);
                connection.getMinecraftConnection().write(response);
            }
        } else if (packet instanceof Disconnect) {
            Disconnect disconnect = (Disconnect) packet;
            connection.disconnect();

            // Do we have an outstanding notification? If so, fulfill it.
            doNotify(ConnectionRequestResults.forDisconnect(disconnect));

            connection.getProxyPlayer().handleConnectionException(connection.getServerInfo(), disconnect);
        } else if (packet instanceof SetCompression) {
            SetCompression sc = (SetCompression) packet;
            connection.getMinecraftConnection().setCompressionThreshold(sc.getThreshold());
        } else if (packet instanceof ServerLoginSuccess) {
            // The player has been logged on to the backend server.
            connection.getMinecraftConnection().setState(StateRegistry.PLAY);
            ServerConnection existingConnection = connection.getProxyPlayer().getConnectedServer();
            if (existingConnection == null) {
                // Strap on the play session handler
                connection.getProxyPlayer().getConnection().setSessionHandler(new ClientPlaySessionHandler(connection.getProxyPlayer()));
            } else {
                // The previous server connection should become obsolete.
                existingConnection.disconnect();
            }

            // Do we have an outstanding notification? If so, fulfill it.
            doNotify(ConnectionRequestResults.SUCCESSFUL);

            connection.getMinecraftConnection().setSessionHandler(new BackendPlaySessionHandler(connection));
            connection.getProxyPlayer().setConnectedServer(connection);
        }
    }

    @Override
    public void deactivated() {
        cancelForwardingCheck();
    }

    @Override
    public void exception(Throwable throwable) {
        connection.getProxyPlayer().handleConnectionException(connection.getServerInfo(), throwable);
    }

    private void doNotify(ConnectionRequestBuilder.Result result) {
        ChannelPipeline pipeline = connection.getMinecraftConnection().getChannel().pipeline();
        ServerConnection.ConnectionNotifier n = pipeline.get(ServerConnection.ConnectionNotifier.class);
        if (n != null) {
            n.getResult().complete(result);
            pipeline.remove(ServerConnection.ConnectionNotifier.class);
        }
    }

    private void cancelForwardingCheck() {
        if (forwardingCheckTask != null) {
            forwardingCheckTask.cancel(false);
            forwardingCheckTask = null;
        }
    }

    private static ByteBuf createForwardingData(String address, GameProfile profile) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolUtils.writeString(buf, address);
        ProtocolUtils.writeUuid(buf, profile.idAsUuid());
        ProtocolUtils.writeString(buf, profile.getName());
        ProtocolUtils.writeVarInt(buf, profile.getProperties().size());
        for (GameProfile.Property property : profile.getProperties()) {
            ProtocolUtils.writeString(buf, property.getName());
            ProtocolUtils.writeString(buf, property.getValue());
            String signature = property.getSignature();
            if (signature != null) {
                buf.writeBoolean(true);
                ProtocolUtils.writeString(buf, signature);
            } else {
                buf.writeBoolean(false);
            }
        }
        return buf;
    }
}
