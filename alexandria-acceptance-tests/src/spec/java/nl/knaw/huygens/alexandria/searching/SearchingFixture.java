package nl.knaw.huygens.alexandria.searching;

/*
 * #%L
 * alexandria-acceptance-tests
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

import static java.util.UUID.randomUUID;

import java.util.UUID;

import org.concordion.integration.junit4.ConcordionRunner;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import nl.knaw.huygens.alexandria.concordion.AlexandriaAcceptanceTest;
import nl.knaw.huygens.alexandria.endpoint.search.SearchEndpoint;

@RunWith(ConcordionRunner.class)
public class SearchingFixture extends AlexandriaAcceptanceTest {
  @BeforeClass
  public static void registerEndpoints() {
    register(SearchEndpoint.class);
  }

  public void setupPagingStorage(String num) {
    clearStorage();

    for (int i = 0; i < Integer.valueOf(num); i++) {
      generateAnnotatedResource(randomUUID());
    }
  }

  private void generateAnnotatedResource(UUID uuid) {
    resourceExists(uuid.toString());
    hasConfirmedAnnotation(theResource(uuid), anAnnotation());
  }

}
