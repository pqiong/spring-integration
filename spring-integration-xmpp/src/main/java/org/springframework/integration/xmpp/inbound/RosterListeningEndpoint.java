/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.xmpp.inbound;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;

import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.MessageHandlingException;
import org.springframework.integration.MessagingException;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.xmpp.core.AbstractXmppConnectionAwareEndpoint;
import org.springframework.util.Assert;

/**
 * Describes an inbound endpoint that is able to login and then emit {@link Message}s when a 
 * particular Presence event happens to the logged in user's {@link Roster}. 
 * (e.g., logged in/out, changed status etc.)
 *
 * @author Josh Long
 * @author Oleg Zhurakousky
 * @since 2.0
 */
public class RosterListeningEndpoint extends AbstractXmppConnectionAwareEndpoint {

	private static final Log logger = LogFactory.getLog(RosterListeningEndpoint.class);

	private volatile MessageChannel requestChannel;

	private final MessagingTemplate messagingTemplate = new MessagingTemplate();
	
	private final EventForwardingRosterListener rosterListener = new EventForwardingRosterListener();


	public RosterListeningEndpoint() {
		super();
	}

	public RosterListeningEndpoint(XMPPConnection xmppConnection) {
		super(xmppConnection);
	}


	/**
	 * @param requestChannel the channel on which the inbound message should be sent
	 */
	public void setRequestChannel(final MessageChannel requestChannel) {
		this.requestChannel = requestChannel;
	}

	@Override
	protected void doStart() {
		Assert.isTrue(this.initialized, this.getComponentName() + "#" + this.getComponentType() + " must be initialized");
		this.xmppConnection.getRoster().addRosterListener(rosterListener);
	}

	@Override
	protected void doStop() {
		this.xmppConnection.getRoster().removeRosterListener(rosterListener);
	}

	@Override
	protected void onInit() throws Exception {
		super.onInit();
		this.messagingTemplate.setDefaultChannel(requestChannel);
		this.messagingTemplate.afterPropertiesSet();
	}

	/**
	 * Called whenever an event happens related to the {@link Roster}.
	 */
	private void forwardRosterEvent(Object event) {	
		Message<?> message = null;
		try {
			message = MessageBuilder.withPayload(event).build();
			this.messagingTemplate.send(this.requestChannel, message);
		}
		catch (MessagingException e) {
			throw e;
		}
		catch (Exception e) {
			throw new MessageHandlingException(message, "Failed to send roster event message", e);
		}
	}

	/**
	 * RosterListener that subscribes to a given {@link Roster}'s events 
	 * and forwards them to a message channel.
	 */
	private class EventForwardingRosterListener implements RosterListener {

		public void entriesAdded(Collection<String> entries) {
			logger.debug("entries added: " + StringUtils.join(entries.iterator(), ","));
			forwardRosterEvent(entries);
		}

		public void entriesUpdated(Collection<String> entries) {
			logger.debug("entries updated: " + StringUtils.join(entries.iterator(), ","));
			forwardRosterEvent(entries);
		}

		public void entriesDeleted(Collection<String> entries) {
			logger.debug("entries deleted: " + StringUtils.join(entries.iterator(), ","));
			forwardRosterEvent(entries);
		}

		public void presenceChanged(Presence presence) {
			logger.debug("presence changed: " + ToStringBuilder.reflectionToString(presence));
			forwardRosterEvent(presence);
		}
	}

}