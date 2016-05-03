/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program;
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307 USA
 */

package com.comphenix.protocol.events;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.EventObject;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.async.AsyncMarker;
import com.comphenix.protocol.error.PluginContext;
import com.comphenix.protocol.error.Report;
import com.comphenix.protocol.error.ReportType;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

/**
 * Represents a packet sending or receiving event. Changes to the packet will be
 * reflected in the final version to be sent or received. It is also possible to cancel an event.
 * @author Kristian
 */
public class PacketEvent extends EventObject implements Cancellable {
	public static final ReportType REPORT_CHANGING_PACKET_TYPE_IS_CONFUSING = new ReportType(
			"Plugin %s changed packet type from %s to %s in packet listener. This is confusing for other plugins! (Not an error, though!)");
	
	private static final SetMultimap<PacketType, PacketType> CHANGE_WARNINGS =
			Multimaps.synchronizedSetMultimap(HashMultimap.<PacketType, PacketType>create());
	
	/**
	 * Automatically generated by Eclipse.
	 */
	private static final long serialVersionUID = -5360289379097430620L;
	
	private transient WeakReference<Player> playerReference;
	private transient Player offlinePlayer;
	
	private PacketContainer packet;
	private boolean serverPacket;
	private boolean cancel;
	
	private AsyncMarker asyncMarker;
	private boolean asynchronous;

	// Network input and output handlers
	NetworkMarker networkMarker;
	
	// Whether or not a packet event is read only
	private boolean readOnly;
	private boolean filtered;
	
	/**
	 * Use the static constructors to create instances of this event.
	 * @param source - the event source.
	 */
	public PacketEvent(Object source) {
		super(source);
		this.filtered = true;
	}

	private PacketEvent(Object source, PacketContainer packet, Player player, boolean serverPacket) {
		this(source, packet, null, player, serverPacket, true);
	}
	
	private PacketEvent(Object source, PacketContainer packet, NetworkMarker marker, Player player, boolean serverPacket, boolean filtered) {
		super(source);
		this.packet = packet;
		this.playerReference = new WeakReference<Player>(player);
		this.networkMarker = marker;
		this.serverPacket = serverPacket;
		this.filtered = filtered;
	}
	
	private PacketEvent(PacketEvent origial, AsyncMarker asyncMarker) {
		super(origial.source);
		this.packet = origial.packet;
		this.playerReference = origial.playerReference;
		this.cancel = origial.cancel;
		this.serverPacket = origial.serverPacket;
		this.filtered = origial.filtered;
		this.networkMarker = origial.networkMarker;
		this.asyncMarker = asyncMarker;
		this.asynchronous = true;
	}

	/**
	 * Creates an event representing a client packet transmission.
	 * @param source - the event source.
	 * @param packet - the packet.
	 * @param client - the client that sent the packet.
	 * @return The event.
	 */
	public static PacketEvent fromClient(Object source, PacketContainer packet, Player client) {
		return new PacketEvent(source, packet, client, false);
	}
	
	/**
	 * Creates an event representing a client packet transmission.
	 * @param source - the event source.
	 * @param packet - the packet.
	 * @param marker - the network marker.
	 * @param client - the client that sent the packet.
	 * @return The event.
	 */
	public static PacketEvent fromClient(Object source, PacketContainer packet, NetworkMarker marker, Player client) {
		return new PacketEvent(source, packet, marker, client, false, true);
	}
	
	/**
	 * Creates an event representing a client packet transmission.
	 * <p>
	 * If <i>filtered</i> is FALSE, then this event is only processed by packet monitors.
	 * @param source - the event source.
	 * @param packet - the packet.
	 * @param marker - the network marker.
	 * @param client - the client that sent the packet.
	 * @param filtered - whether or not this packet event is processed by every packet listener.
	 * @return The event.
	 */
	public static PacketEvent fromClient(Object source, PacketContainer packet, NetworkMarker marker, Player client, boolean filtered) {
		return new PacketEvent(source, packet, marker, client, false, filtered);
	}
	
	/**
	 * Creates an event representing a server packet transmission.
	 * @param source - the event source.
	 * @param packet - the packet.
	 * @param recipient - the client that will receieve the packet.
	 * @return The event.
	 */
	public static PacketEvent fromServer(Object source,  PacketContainer packet, Player recipient) {
		return new PacketEvent(source, packet, recipient, true);
	}
	
	/**
	 * Creates an event representing a server packet transmission.
	 * @param source - the event source.
	 * @param packet - the packet.
	 * @param marker - the network marker.
	 * @param recipient - the client that will receieve the packet.
	 * @return The event.
	 */
	public static PacketEvent fromServer(Object source, PacketContainer packet, NetworkMarker marker, Player recipient) {
		return new PacketEvent(source, packet, marker, recipient, true, true);
	}
	
	/**
	 * Creates an event representing a server packet transmission.
	 * <p>
	 * If <i>filtered</i> is FALSE, then this event is only processed by packet monitors.
	 * @param source - the event source.
	 * @param packet - the packet.
	 * @param marker - the network marker.
	 * @param recipient - the client that will receieve the packet.
	 * @param filtered - whether or not this packet event is processed by every packet listener.
	 * @return The event.
	 */
	public static PacketEvent fromServer(Object source, PacketContainer packet, NetworkMarker marker, Player recipient, boolean filtered) {
		return new PacketEvent(source, packet, marker, recipient, true, filtered);
	}
	
	/**
	 * Create an asynchronous packet event from a synchronous event and a async marker.
	 * @param event - the original synchronous event.
	 * @param marker - the asynchronous marker.
	 * @return The new packet event.
	 */
	public static PacketEvent fromSynchronous(PacketEvent event, AsyncMarker marker) {
		return new PacketEvent(event, marker);
	}
	
	/**
	 * Determine if we are executing the packet event in an asynchronous thread.
	 * <p>
	 * If so, you must synchronize all calls to the Bukkit API.
	 * <p>
	 * Generally, most server packets are executed on the main thread, whereas client packets
	 * are all executed asynchronously.
	 * @return TRUE if we are, FALSE otherwise.
	 */
	public boolean isAsync() {
		return !Bukkit.isPrimaryThread();
	}
	
	/**
	 * Retrieves the packet that will be sent to the player.
	 * @return Packet to send to the player.
	 */
	public PacketContainer getPacket() {
		return packet;
	}

	/**
	 * Replace the packet that will be sent to the player.
	 * @param packet - the packet that will be sent instead.
	 */
	public void setPacket(PacketContainer packet) {
		if (readOnly)
			throw new IllegalStateException("The packet event is read-only.");
		if (packet == null)
			throw new IllegalArgumentException("Cannot set packet to NULL. Use setCancelled() instead.");
		
		// Change warnings
		final PacketType oldType = this.packet.getType();
		final PacketType newType = packet.getType();
		if (this.packet != null && !Objects.equal(oldType, newType)) {
			// Only report this once
			if (CHANGE_WARNINGS.put(oldType, newType)) {
				ProtocolLibrary.getErrorReporter().reportWarning(this,
						Report.newBuilder(REPORT_CHANGING_PACKET_TYPE_IS_CONFUSING).
						messageParam(PluginContext.getPluginCaller(new Exception()), oldType, newType).
						build());
			}
		}
		this.packet = packet;
	}
	
	/**
	 * Retrieves the packet ID.
	 * <p>
	 * Deprecated: Use {@link #getPacketType()} instead.
	 * @return The current packet ID.
	 */
	@Deprecated
	public int getPacketID() {
		return packet.getID();
	}
	
	/**
	 * Retrieve the packet type.
	 * @return The type.
	 */
	public PacketType getPacketType() {
		return packet.getType();
	}
	
	/**
	 * Retrieves whether or not the packet should be cancelled.
	 * @return TRUE if it should be cancelled, FALSE otherwise.
	 */
	@Override
	public boolean isCancelled() {
		return cancel;
	}
	
	/**
	 * Retrieve the object responsible for managing the serialized input and output of a packet.
	 * <p>
	 * Note that the serialized input data is only available for client-side packets, and the output handlers
	 * can only be applied to server-side packets.
	 * @return The network manager.
	 */
	public NetworkMarker getNetworkMarker() {
		if (networkMarker == null) {
			if (isServerPacket()) {
				networkMarker = new NetworkMarker.EmptyBufferMarker(
					serverPacket ? ConnectionSide.SERVER_SIDE : ConnectionSide.CLIENT_SIDE);
			} else {
				throw new IllegalStateException("Add the option ListenerOptions.INTERCEPT_INPUT_BUFFER to your listener.");
			}
		}
		return networkMarker;
	}

	/**
	 * Update the network manager.
	 * <p>
	 * This method is internal - do not call.
	 * @param networkMarker - the new network manager.
	 */
	public void setNetworkMarker(NetworkMarker networkMarker) {
		this.networkMarker = Preconditions.checkNotNull(networkMarker, "marker cannot be NULL");
	}
	
	/**
	 * Sets whether or not the packet should be cancelled. Uncancelling is possible.
	 * <p>
	 * <b>Warning</b>: A cancelled packet should never be re-transmitted. Use the asynchronous
	 * packet manager if you need to perform extensive processing. It should also be used
	 * if you need to synchronize with the main thread.
	 * <p>
	 * This ensures that other plugins can work with the same packet.
	 * <p>
	 * An asynchronous listener can also delay a packet indefinitely without having to block its thread.
	 * 
	 * @param cancel - TRUE if it should be cancelled, FALSE otherwise.
	 */
	@Override
	public void setCancelled(boolean cancel) {
		if (readOnly)
			throw new IllegalStateException("The packet event is read-only.");
		this.cancel = cancel;
	}

	/**
	 * Retrieves the player that has sent the packet or is recieving it.
	 * @return The player associated with this event.
	 */
	public Player getPlayer() {
		return playerReference.get();
	}
	
	/**
	 * Determine if this packet is filtered by every packet listener.
	 * <p>
	 * If not, it will only be intercepted by monitor packets.
	 * @return TRUE if it is, FALSE otherwise.
	 */
	public boolean isFiltered() {
		return filtered;
	}
	
	/**
	 * Whether or not this packet was created by the server.
	 * <p>
	 * Most listeners can deduce this by noting which listener method was invoked.
	 * @return TRUE if the packet was created by the server, FALSE if it was created by a client.
	 */
	public boolean isServerPacket() {
		return serverPacket;
	}
	
	/**
	 * Retrieve the asynchronous marker.
	 * <p>
	 * If the packet is synchronous, this marker will be used to schedule an asynchronous event. In the following
	 * asynchronous event, the marker is used to correctly pass the packet around to the different threads.
	 * <p>
	 * Note that if there are no asynchronous events that can receive this packet, the marker is NULL.
	 * @return The current asynchronous marker, or NULL.
	 */
	public AsyncMarker getAsyncMarker() {
		return asyncMarker;
	}
	/**
	 * Set the asynchronous marker.
	 * <p>
	 * If the marker is non-null at the end of an synchronous event processing, the packet will be scheduled
	 * to be processed asynchronously with the given settings.
	 * <p>
	 * Note that if there are no asynchronous events that can receive this packet, the marker should be NULL.
	 * @param asyncMarker - the new asynchronous marker, or NULL.
	 * @throws IllegalStateException If the current event is asynchronous.
	 */
	public void setAsyncMarker(AsyncMarker asyncMarker) {
		if (isAsynchronous())
			throw new IllegalStateException("The marker is immutable for asynchronous events");
		if (readOnly)
			throw new IllegalStateException("The packet event is read-only.");
		this.asyncMarker = asyncMarker;
	}

	/**
	 * Determine if the current packet event is read only.
	 * <p>
	 * This is used to ensure that a monitor listener doesn't accidentally alter the state of the event. However,
	 * it is still possible to modify the packet itself, as it would require too many resources to verify its integrity.
	 * <p>
	 * Thus, the packet is considered immutable if the packet event is read only.
	 * @return TRUE if it is, FALSE otherwise.
	 */
	public boolean isReadOnly() {
		return readOnly;
	}
	
	/**
	 * Set the read-only state of this packet event.
	 * <p>
	 * This will be reset for every packet listener.
	 * @param readOnly - TRUE if it is read-only, FALSE otherwise.
	 */
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
	
	/**
	 * Determine if the packet event has been executed asynchronously or not.
	 * @return TRUE if this packet event is asynchronous, FALSE otherwise.
	 */
	public boolean isAsynchronous() {
		return asynchronous;
	}

	/**
	 * Schedule a packet for sending or receiving after the current packet event is successful.
	 * <p>
	 * The packet will be added to {@link NetworkMarker#getScheduledPackets()}.
	 * @param scheduled - the packet to transmit or receive.
	 */
	public void schedule(ScheduledPacket scheduled) {
		getNetworkMarker().getScheduledPackets().add(scheduled);
	}
	
	/**
	 * Unschedule a specific packet.
	 * @param scheduled - the scheduled packet.
	 * @return TRUE if it was unscheduled, FALSE otherwise.
	 */
	public boolean unschedule(ScheduledPacket scheduled) {
		if (networkMarker != null) {
			return networkMarker.getScheduledPackets().remove(scheduled);
		}
		return false;
	}
	
	private void writeObject(ObjectOutputStream output) throws IOException {
	    // Default serialization
		output.defaultWriteObject();

		// Write the name of the player (or NULL if it's not set)
		output.writeObject(playerReference.get() != null ? new SerializedOfflinePlayer(playerReference.get()) : null);
	}

	private void readObject(ObjectInputStream input) throws ClassNotFoundException, IOException {
	    // Default deserialization
		input.defaultReadObject();

		final SerializedOfflinePlayer serialized = (SerializedOfflinePlayer) input.readObject();
		
		// Better than nothing
	    if (serialized != null) {
	    	// Store it, to prevent weak reference from cleaning up the reference
	    	offlinePlayer = serialized.getPlayer();
	    	playerReference = new WeakReference<Player>(offlinePlayer);
	    }
	}

	@Override
	public String toString() {
		return "PacketEvent[player=" + getPlayer() + ", packet=" + packet + "]";
	}
}