import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Field;
import org.zendesk.client.v2.model.Group;
import org.zendesk.client.v2.model.GroupMembership;
//import org.zendesk.client.v2.model.JobStatus;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Trigger;
import org.zendesk.client.v2.model.User;

public class UniconZendeskImport {

    private static final Logger log = LoggerFactory.getLogger(UniconZendeskImport.class);

    private static Zendesk ZENDESK_FROM;
    private static Zendesk ZENDESK_TO;
    private static final String FROM_ZENDESK_URL = "https://unicon.zendesk.com";
    private static final String FROM_ZENDESK_USERNAME = "rlong@unicon.net";
    private static final String FROM_ZENDESK_TOKEN = "4anl5E9L4jaDfsTmTmePoF71MYAbst8ubxcUnqVg";
    private static final String TO_ZENDESK_URL = "https://uni-trial.zendesk.com";
    private static final String TO_ZENDESK_USERNAME = "zd_trial@yahoo.com";
    private static final String TO_ZENDESK_TOKEN = "ZrXsSEEHDLQATGl4CCNUSvNJ8rAMxCKYd14n4OO5";

    /**
     * Organizations to import
     */
    private static Long[] organizationIds = new Long[] {
        21616897L, // Vandelay Inductries
        23783082L // Bogus Enterprises
    };

    /**
     * Groups to import
     */
    private static Long[] groupIds = new Long[] {
        20618411L // Test Support Group
    };

    public static void main(String[] args) {
        UniconZendeskImport uniconZendeskImport = new UniconZendeskImport();

        log.info("Starting import of data from {} to {}", FROM_ZENDESK_URL, TO_ZENDESK_URL);

        ZENDESK_FROM = new Zendesk.Builder(FROM_ZENDESK_URL)
            .setUsername(FROM_ZENDESK_USERNAME)
            .setToken(FROM_ZENDESK_TOKEN)
            .build();

        ZENDESK_TO = new Zendesk.Builder(TO_ZENDESK_URL)
            .setUsername(TO_ZENDESK_USERNAME)
            .setToken(TO_ZENDESK_TOKEN)
            .build();

        uniconZendeskImport.importOrganizations();
        uniconZendeskImport.importGroups();
        uniconZendeskImport.importTriggers();
        uniconZendeskImport.importTicketFields();

        log.info("Import of data from {} to {} COMPLETED", FROM_ZENDESK_URL, TO_ZENDESK_URL);
    }

    private void importOrganizations() {
        log.info("Importing organizations...");

        for (Long fromOrganizationId : organizationIds) {
            Organization fromOrganization = ZENDESK_FROM.getOrganization(fromOrganizationId);

            if (fromOrganization == null) {
                log.error("No organization found with ID {}", Long.toString(fromOrganizationId));
                continue;
            }

            // import organization
            Organization newOrganization = importOrganization(fromOrganization);
            log.info("Imported organization:: new id: {}, name: {}", newOrganization.getId(), newOrganization.getName());

            // import users from organization
            Iterable<User> organizationUsers = ZENDESK_FROM.getOrganizationUsers(fromOrganizationId);

            importUsersIntoOrganization(organizationUsers, newOrganization.getId());
        }
    }

    private Organization importOrganization(Organization fromOrganization) {
        log.info("Importing organization:: id: {}, name: {}", fromOrganization.getId(), fromOrganization.getName());
        Organization toOrganization = fromOrganization;
        toOrganization.setId(null);
        toOrganization.setCreatedAt(null);
        toOrganization.setUpdatedAt(null);

        Iterable<Organization> existingToOrganizations = ZENDESK_TO.getOrganizations();
        for (Organization existingToOrganization : existingToOrganizations) {
            if (existingToOrganization.getName().equals(fromOrganization.getName())) {
                // organization exists, just return it
                return existingToOrganization;
            }
        }

        return ZENDESK_TO.createOrganization(toOrganization);
    }

    private void importUsersIntoOrganization(Iterable<User> existingFromUsers, Long toOrganizationId) {
        log.info("Importing users into organization: {}", toOrganizationId);

        Iterable<User> existingToUsers = ZENDESK_TO.getUsers();
        List<User> usersToCreate = new ArrayList<>();

        Iterator<User> i = existingFromUsers.iterator();

        usersLoop:
        while (i.hasNext()) {
            User processingUser = i.next();

            log.info("Processing user:: name: {}, email: {}", processingUser.getName(), processingUser.getEmail());

            for (User existingToUser : existingToUsers) {
                if (existingToUser.getEmail().equals(processingUser.getEmail())) {
                    // user exists, skip creation
                    log.info("Processing user:: name: {}, email: {} :: USER EXISTS ALREADY... SKIPPING", processingUser.getName(), processingUser.getEmail());
                    continue usersLoop;
                }
            }

            processingUser.setId(null);
            processingUser.setUrl(null);
            processingUser.setCreatedAt(null);
            processingUser.setUpdatedAt(null);
            processingUser.setActive(null);
            processingUser.setShared(null);
            processingUser.setLocaleId(null);
            processingUser.setLastLoginAt(null);
            processingUser.setOrganizationId(toOrganizationId);

            usersToCreate.add(processingUser);
        }

        createUsers(usersToCreate);
    }

    private void createUsers(List<User> usersToCreate) {
        for (User userToCreate : usersToCreate) {

            User createdUser = null;

            try {
                createdUser = ZENDESK_TO.createUser(userToCreate);
                log.info("Created/updated user on {}:: name: {}, email: {}, id: {} ", TO_ZENDESK_URL, createdUser.getName(), createdUser.getEmail(), Long.toString(createdUser.getId()));
            } catch (Exception e) {
                log.error("Error creating user:: name: {}, email: {}", userToCreate.getName(), userToCreate.getEmail(), e);
            }
        }
    }

    private void importGroups() {
        log.info("Importing groups...");

        Iterable<Group> existingToGroups = ZENDESK_TO.getGroups();

        groupIdLoop:
        for (Long groupId : groupIds) {
            Group existingFromGroup = ZENDESK_FROM.getGroup(groupId);

            if (existingFromGroup == null) {
                log.error("Group not found with ID: {}", groupId);
                continue groupIdLoop;
            }

            log.info("Importing group:: name: {}, id: {}", existingFromGroup.getName(), existingFromGroup.getId());

            for (Group existingToGroup : existingToGroups) {
                if (existingToGroup.getName().equals(existingFromGroup.getName())) {
                    // group exists, skip creation
                    log.info("Importing group:: name: {}, id: {} :: GROUP EXISTS ALREADY... SKIPPING", existingFromGroup.getName(), existingFromGroup.getId());
                    continue groupIdLoop;
                }
            }

            existingFromGroup.setId(null);
            Group newGroup = ZENDESK_TO.createGroup(existingFromGroup);

            importUsersIntoGroup(groupId, newGroup);
        }
    }

    private void importUsersIntoGroup(Long fromGroupId, Group toGroup) {
        log.info("Importing users into group: {}", toGroup.getName());

        Long toGroupId = toGroup.getId();
        Iterable<User> existingFromGroupUsers = ZENDESK_FROM.getGroupUsers(fromGroupId);
        List<GroupMembership> toGroupMemberships = ZENDESK_TO.getGroupMemberships(toGroupId);
        Iterable<User> existingUsers = ZENDESK_TO.getUsers();

        Iterator<User> i = existingFromGroupUsers.iterator();

        usersLoop:
        while (i.hasNext()) {
            User processingUser = null;
            User existingFromGroupUser = i.next();

            // create user
            for (User existingUser : existingUsers) {
                if (existingUser.getEmail().equals(existingFromGroupUser.getEmail())) {
                    // user exists, skip creation
                    processingUser = existingUser;
                    break;
                }
            }

            if (processingUser == null) {
                existingFromGroupUser.setId(null);
                existingFromGroupUser.setUrl(null);
                existingFromGroupUser.setCreatedAt(null);
                existingFromGroupUser.setUpdatedAt(null);
                existingFromGroupUser.setActive(null);
                existingFromGroupUser.setShared(null);
                existingFromGroupUser.setLocaleId(null);
                existingFromGroupUser.setLastLoginAt(null);
                existingFromGroupUser.setOrganizationId(null);

                processingUser = ZENDESK_TO.createUser(existingFromGroupUser);
                log.info("Created/updated user on {}:: name: {}, email: {} ", TO_ZENDESK_URL, processingUser.getName(), processingUser.getEmail(), Long.toString(processingUser.getId()));
            }

            // add user to group
            for (GroupMembership toGroupMembership : toGroupMemberships) {
                if (toGroupMembership.getUserId().longValue() == processingUser.getId().longValue()) {
                    // user exists in group, skip adding
                    log.info("User {} exists in group {} already... SKIPPING", processingUser.getName(), toGroup.getName());
                    continue usersLoop;
                }
            }

            GroupMembership newGroupMembership = new GroupMembership();
            newGroupMembership.setUserId(processingUser.getId());
            newGroupMembership.setGroupId(toGroupId);

            try {
                ZENDESK_TO.createGroupMembership(newGroupMembership);
                log.info("Added user: {} to group: {}", processingUser.getName(), toGroup.getName());
            } catch (Exception e) {
                log.error("Error adding user: {} to group: {}", processingUser.getName(), toGroup.getName(), e);
            }
        }
    }

    private void importTriggers() {
        log.info("Importing triggers...");

        Iterable<Trigger> existingFromTriggers = ZENDESK_FROM.getTriggers();
        Iterable<Trigger> existingToTriggers = ZENDESK_TO.getTriggers();
        Iterator<Trigger> i = existingFromTriggers.iterator();

        triggerLoop:
        while (i.hasNext()) {
            Trigger processingTrigger = i.next();

            for (Trigger existingToTrigger : existingToTriggers) {
                if (existingToTrigger.getTitle().equals(processingTrigger.getTitle())) {
                    log.info("Trigger {} exists already... SKIPPING", processingTrigger.getTitle());
                    continue triggerLoop;
                }
            }

            processingTrigger.setId(null);
            processingTrigger.setCreatedAt(null);
            processingTrigger.setUpdatedAt(null);

            try {
                ZENDESK_TO.createTrigger(processingTrigger);
                log.info("Added trigger:: {}", processingTrigger.getTitle());
            } catch (Exception e) {
                // unspecified error, so we'll just skip for now
                continue triggerLoop;
            }
        }
    }

    private void importTicketFields() {
        log.info("Importing ticket fields...");

        List<Field> existingFromTicketFields = ZENDESK_FROM.getTicketFields();
        List<Field> existingToTicketFields = ZENDESK_TO.getTicketFields();
        Iterator<Field> i = existingFromTicketFields.iterator();

        ticketFieldLoop:
            while (i.hasNext()) {
                Field processingTicketField = i.next();

                for (Field existingToTicketField : existingToTicketFields) {
                    if (existingToTicketField.getTitle().equals(processingTicketField.getTitle())) {
                        log.info("Ticket field {} exists already... SKIPPING", processingTicketField.getTitle());
                        continue ticketFieldLoop;
                    }
                }

                processingTicketField.setId(null);
                processingTicketField.setCreatedAt(null);
                processingTicketField.setUpdatedAt(null);
                processingTicketField.setSystemFieldOptions(null);
                processingTicketField.setRemovable(null);

                try {
                    ZENDESK_TO.createTicketField(processingTicketField);
                    log.info("Added ticket field:: {}", processingTicketField.getTitle());
                } catch (Exception e) {
                    log.error("Error adding ticket field: {}", processingTicketField.getTitle(), e);
                    continue ticketFieldLoop;
                }
            }
    }
}
