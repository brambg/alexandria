package nl.knaw.huygens.alexandria.client;

/*
 * #%L
 * alexandria-java-client
 * =======
 * Copyright (C) 2015 - 2016 Huygens ING (KNAW)
 * =======
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import nl.knaw.huygens.alexandria.api.model.CommandResponse;
import nl.knaw.huygens.alexandria.api.model.Commands;

public class CommandTest extends AlexandriaClientTest {

  @Before
  public void before() {
    client.setAuthKey(AUTHKEY);
    client.setAutoConfirm(true);
  }

  @Test
  public void testAddUniqueIdCommmandWorks() {
    UUID resourceUuid = createResource("xml");
    String xml = "<text><p>Alinea 1</p><p>Alinea 2</p></text>";
    String expectedXml = singleQuotesToDouble("<text xml:id='text-1'><p xml:id='p-1'>Alinea 1</p><p xml:id='p-2'>Alinea 2</p></text>");
    setResourceText(resourceUuid, xml);
    Map<String, Object> parameters = ImmutableMap.<String, Object> builder()//
        .put("resourceIds", ImmutableList.of(resourceUuid))//
        .put("elements", ImmutableList.of("text", "p"))//
        .build();
    RestResult<CommandResponse> result = client.addCommand(Commands.ADD_UNIQUE_ID, parameters);
    assertRequestSucceeded(result);
    CommandResponse commandResponse = result.get();
    assertThat(commandResponse.success()).isTrue();

    RestResult<String> xmlReadResult = client.getTextAsString(resourceUuid);
    assertRequestSucceeded(xmlReadResult);
    String xml2 = xmlReadResult.get();
    assertThat(xml2).isEqualTo(expectedXml);
  }

}
