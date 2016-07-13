import java.util.Arrays;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Field;
import org.zendesk.client.v2.model.Group;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Trigger;
import org.zendesk.client.v2.model.User;

public class UniconZendeskImportReset {

    private static final Logger log = LoggerFactory.getLogger(UniconZendeskImportReset.class);

    private static Zendesk ZENDESK;
    private static final String ZENDESK_URL = "https://uni-trial.zendesk.com";
    private static final String ZENDESK_USERNAME = "zd_trial@yahoo.com";
    private static final String ZENDESK_TOKEN = "ZrXsSEEHDLQATGl4CCNUSvNJ8rAMxCKYd14n4OO5";
    private static final long[] ZENDESK_SKIP_USER_IDS = {8038551968L};
    private static final long[] ZENDESK_SKIP_ORGANIZATION_IDS = {7611954708L};
    private static final long[] ZENDESK_SKIP_GROUP_IDS = {29589988L};
    private static final long[] ZENDESK_SKIP_TRIGGER_IDS = {};
    private static final long[] ZENDESK_SKIP_FIELD_IDS = {34854008L, 34854028L, 34854048L, 34854068L, 34854088L, 34854108L, 34854128L};

    public static void main(String[] args) {
        log.info("Starting reset of data in {}", ZENDESK_URL);

        UniconZendeskImportReset uniconZendeskImportReset = new UniconZendeskImportReset();

        ZENDESK = new Zendesk.Builder(ZENDESK_URL)
            .setUsername(ZENDESK_USERNAME)
            .setToken(ZENDESK_TOKEN)
            .build();

        uniconZendeskImportReset.deleteOrganizations();
        uniconZendeskImportReset.deleteGroups();
        uniconZendeskImportReset.deleteTriggers();
        uniconZendeskImportReset.deleteTicketFields();
        uniconZendeskImportReset.deleteUsers();

        log.info("Reset of data from {} COMPLETED", ZENDESK_URL);
    }

    private void deleteOrganizations() {
        log.info("DELETING ORGANIZATIONS...");
        Iterable<Organization> organizations = ZENDESK.getOrganizations();

        for (final Organization organization : organizations) {
            LongStream longStream = Arrays.stream(ZENDESK_SKIP_ORGANIZATION_IDS);

            if (longStream.anyMatch(x -> x == organization.getId().longValue())) {
                log.info("Not deleting organization: {}", organization.getName());
                continue;
            }

            ZENDESK.deleteOrganization(organization);
            log.info("Deleted organization:: {}", organization.getName());
        }
    }

    private void deleteGroups() {
        log.info("DELETING GROUPS...");
        Iterable<Group> groups = ZENDESK.getGroups();

        for (final Group group : groups) {
            LongStream longStream = Arrays.stream(ZENDESK_SKIP_GROUP_IDS);

            if (longStream.anyMatch(x -> x == group.getId().longValue())) {
                log.info("Not deleting group: {}", group.getName());
                continue;
            }

            ZENDESK.deleteGroup(group);
            log.info("Deleted group:: {}", group.getName());
        }
    }

    private void deleteUsers() {
        log.info("DELETING USERS...");
        Iterable<User> users = ZENDESK.getUsers();

        for (User user : users) {
            LongStream longStream = Arrays.stream(ZENDESK_SKIP_USER_IDS);

            if (longStream.anyMatch(x -> x == user.getId().longValue())) {
                log.info("Not deleting user: name: {}, email: {}", user.getName(), user.getEmail());
                continue;
            }

            ZENDESK.deleteUser(user);
            log.info("Deleted user:: name: {}, email: {}", user.getName(), user.getEmail());
        }
    }

    private void deleteTriggers() {
        log.info("DELETING TRIGGERS...");
        Iterable<Trigger> triggers = ZENDESK.getTriggers();

        for (Trigger trigger : triggers) {
            LongStream longStream = Arrays.stream(ZENDESK_SKIP_TRIGGER_IDS);

            if (longStream.anyMatch(x -> x == trigger.getId().longValue())) {
                log.info("Not deleting trigger: {}", trigger.getTitle());
                continue;
            }

            ZENDESK.deleteTrigger(trigger.getId());
            log.info("Deleted trigger:: title: {}", trigger.getTitle());
        }
    }

    private void deleteTicketFields() {
        log.info("DELETING TICKET FIELDS...");
        Iterable<Field> ticketFields = ZENDESK.getTicketFields();

        for (Field ticketField : ticketFields) {
            LongStream longStream = Arrays.stream(ZENDESK_SKIP_FIELD_IDS);

            if (longStream.anyMatch(x -> x == ticketField.getId().longValue())) {
                log.info("Not deleting ticket field: {}", ticketField.getTitle());
                continue;
            }

            ZENDESK.deleteTicketField(ticketField.getId());
            log.info("Deleted ticket field:: title: {}", ticketField.getTitle());
        }
    }

}
