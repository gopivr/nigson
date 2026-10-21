package com.nigson.integration.notification;

import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.Problem;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class NotificationTemplateValuesTest {
    private static final String SCHEMA="{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\",\"maxLength\":80}},\"required\":[\"name\"]}";
    private static final String INPUT="{\"code\":\"WELCOME\",\"revision\":1,\"locale\":\"en-NG\",\"channel\":\"EMAIL\",\"classification\":\"TRANSACTIONAL\",\"subject\":\"Hello\",\"body\":\"<p>Hello {{name}}</p>\",\"variablesSchema\":"+SCHEMA+",\"active\":false}";
    @Test void closedDataTemplateRendersHtmlEscapedSubstitutions(){var command=NotificationTemplateValues.parse(EventCodec.JSON.readTree(INPUT));assertThat(NotificationTemplateValues.render(command.body(),Map.of("name","<script>&\"'"),true)).isEqualTo("<p>Hello &lt;script&gt;&amp;&quot;&#39;</p>");assertThat(NotificationTemplateValues.render(command.body(),Map.of("name","A&B"),false)).isEqualTo("<p>Hello A&B</p>");}
    @Test void rejectsExecutableUnknownOrUnboundedTemplateFields(){for(String body:java.util.List.of("{{{name}}}","{{unknown}}","{% if name %}","${name}")){String bad=INPUT.replace("<p>Hello {{name}}</p>",body);assertThatThrownBy(()->NotificationTemplateValues.parse(EventCodec.JSON.readTree(bad))).isInstanceOf(Problem.Failure.class);}String badSchema=INPUT.replace("\"additionalProperties\":false","\"additionalProperties\":true");assertThatThrownBy(()->NotificationTemplateValues.parse(EventCodec.JSON.readTree(badSchema))).isInstanceOf(Problem.Failure.class);}
    @Test void commandDoesNotExposeSubmittedTemplateInStringRepresentation(){var command=NotificationTemplateValues.parse(EventCodec.JSON.readTree(INPUT));assertThat(command.toString()).doesNotContain("<p>Hello").contains("protected");}
}
