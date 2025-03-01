/*
   Copyright 2021-2023 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.receivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Test;

import nl.nn.adapterframework.core.ListenerException;
import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.jdbc.MessageStoreListener;
import nl.nn.adapterframework.jdbc.dbms.GenericDbmsSupport;
import nl.nn.adapterframework.jndi.JndiDataSourceFactory;
import nl.nn.adapterframework.stream.Message;

@SuppressWarnings("unchecked")
public class MessageStoreListenerTest<M> extends ListenerTestBase<M, MessageStoreListener<M>> {

	@Override
	public MessageStoreListener<M> createListener() throws Exception {
		MessageStoreListener listener = spy(new MessageStoreListener() {
			@Override
			protected MessageWrapper<Object> getRawMessage(Connection conn, Map threadContext) throws ListenerException {
				//super class JdbcListener always wraps this in a MessageWrapper
				return new MessageWrapper<>(Message.asMessage(threadContext.get(STUB_RESULT_KEY)), String.valueOf(threadContext.get(PipeLineSession.MESSAGE_ID_KEY)), null);
			}
		});
		DatabaseMetaData md = mock(DatabaseMetaData.class);
		doReturn("product").when(md).getDatabaseProductName();
		doReturn("version").when(md).getDatabaseProductVersion();
		Connection conn = mock(Connection.class);
		doReturn(md).when(conn).getMetaData();
		JndiDataSourceFactory factory = new JndiDataSourceFactory();
		DataSource dataSource = mock(DataSource.class);
		String dataSourceName = "myDummyDataSource";
		factory.add(dataSource, dataSourceName);
		listener.setDataSourceFactory(factory);
		doReturn(conn).when(dataSource).getConnection();
		listener.setConnectionsArePooled(false);
		listener.setDatasourceName(dataSourceName);
		doReturn(new GenericDbmsSupport()).when(listener).getDbmsSupport();
		return listener;
	}

	@Test
	public void basic() throws Exception {
		listener.configure();
		listener.open();

		String input = "test-message";
		RawMessageWrapper<M> rawMessage = getRawMessage(input);
		assertTrue(rawMessage instanceof MessageWrapper);
		assertEquals("MessageStoreListener should not manipulate the rawMessage", input, ((MessageWrapper<Object>)rawMessage).getMessage().asString());
	}

	@Test
	public void withSessionKeys() throws Exception {
		listener.setSessionKeys("sessionKey1,sessionKey2,sessionKey3");
		listener.configure();
		listener.open();

		String input = "test-message,\"value1\",value2,value3";
		RawMessageWrapper<M> rawMessage = getRawMessage(input);
		assertTrue(rawMessage instanceof MessageWrapper);
		Message message = listener.extractMessage(rawMessage, threadContext);
		assertEquals("test-message", message.asString());

		assertEquals("value1", threadContext.get("sessionKey1"));
		assertEquals("value2", threadContext.get("sessionKey2"));
		assertEquals("value3", threadContext.get("sessionKey3"));
	}
}
