package nl.knaw.huygens.alexandria.endpoint.command;

/*
 * #%L
 * alexandria-main
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

import java.util.Map;

import javax.inject.Inject;

import nl.knaw.huygens.alexandria.api.model.CommandResponse;
import nl.knaw.huygens.alexandria.api.model.Commands;
import nl.knaw.huygens.alexandria.service.AlexandriaService;

public class AlexandriaCommandProcessor {

  private AlexandriaService service;

  @Inject
  public AlexandriaCommandProcessor(AlexandriaService service) {
    this.service = service;
  }

  public CommandResponse process(String command, Map<String, Object> parameterMap) {
    switch (command) {
    case Commands.ADD_UNIQUE_ID:
      return new AddUniqueIdCommand(service).runWith(parameterMap);

    case Commands.WRAP_CONTENT_IN_ELEMENT:
      return new WrapContentInElementCommand(service).runWith(parameterMap);

    default:
      return new CommandResponse().addErrorLine("command '" + command + "' not known.");
    }

  }

}
