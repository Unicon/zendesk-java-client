import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Group;
import org.zendesk.client.v2.model.GroupMembership;
import org.zendesk.client.v2.model.JobStatus;
import org.zendesk.client.v2.model.Organization;
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
    }

    private void importOrganizations() {
        for (Long fromOrganizationId : organizationIds) {
            Organization fromOrganization = ZENDESK_FROM.getOrganization(fromOrganizationId);

            if (fromOrganization == null) {
                log.error("No organization found with ID {}", Long.toString(fromOrganizationId));
                continue;
            }

            // import organization
            Organization newOrganization = importOrganization(fromOrganization);

            // import users from organization
            Iterable<User> organizationUsers = ZENDESK_FROM.getOrganizationUsers(fromOrganizationId);

            importUsersIntoOrganization(organizationUsers, newOrganization.getId());
        }
    }

    private Organization importOrganization(Organization fromOrganization) {
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

    private void importUsersIntoOrganization(Iterable<User> existingFromUsers, long toOrganizationId) {
        Iterable<User> existingToUsers = ZENDESK_TO.getUsers();
        List<User> usersToCreate = new ArrayList<>();

        Iterator<User> i = existingFromUsers.iterator();

        usersLoop:
        while (i.hasNext()) {
            User processingUser = i.next();
            for (User existingToUser : existingToUsers) {
                if (existingToUser.getEmail().equals(processingUser.getEmail())) {
                    // user exists, skip creation
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
        JobStatus<User> jobStatus = ZENDESK_TO.createUsers(usersToCreate);

        while (JobStatus.JobStatusEnum.working == jobStatus.getStatus() || JobStatus.JobStatusEnum.queued == jobStatus.getStatus()) {
            log.info("Creating users:: users created: {}", jobStatus.getProgress());
            try {
                Thread.sleep(2000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            jobStatus = ZENDESK_TO.getJobStatus(jobStatus);

            if (JobStatus.JobStatusEnum.failed == jobStatus.getStatus()) {
                log.error("Error importing:: {}", jobStatus.getMessage());
                break;
            }
            if (JobStatus.JobStatusEnum.killed == jobStatus.getStatus()) {
                log.error("Killed importing:: {}", jobStatus.getMessage());
                break;
            }
            if (JobStatus.JobStatusEnum.completed == jobStatus.getStatus()) {
                log.info("Importing users COMPLETED. {}", jobStatus.getMessage());
                break;
            }
        }

        for (User newUser : jobStatus.getResults()) {
            log.info("Creating/updating user on {}:: name: {}, email: {} ", TO_ZENDESK_URL, newUser.getName(), newUser.getEmail());
        }
    }

    private void importGroups() {
        Iterable<Group> existingToGroups = ZENDESK_TO.getGroups();

        groupIdLoop:
        for (Long groupId : groupIds) {
            Group existingFromGroup = ZENDESK_FROM.getGroup(groupId);

            if (existingFromGroup == null) {
                log.error("Group not found with ID: {}", groupId);
                continue groupIdLoop;
            }

            for (Group existingToGroup : existingToGroups) {
                if (existingToGroup.getName().equals(existingFromGroup.getName())) {
                    // group exists, skip creation
                    continue groupIdLoop;
                }
            }

            existingFromGroup.setId(null);
            Group newGroup = ZENDESK_TO.createGroup(existingFromGroup);

            importUsersIntoGroup(groupId, newGroup);
        }
    }

    private void importUsersIntoGroup(long fromGroupId, Group toGroup) {
        long toGroupId = toGroup.getId();
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
                processingUser = ZENDESK_TO.createUser(existingFromGroupUser);
            }

            // add user to group
            for (GroupMembership toGroupMembership : toGroupMemberships) {
                if (toGroupMembership.getUserId() == existingFromGroupUser.getId()) {
                    // user exists in group, skip adding
                    continue usersLoop;
                }
            }

            GroupMembership newGroupMembership = new GroupMembership();
            newGroupMembership.setUserId(processingUser.getId());
            newGroupMembership.setGroupId(toGroupId);

            ZENDESK_TO.createGroupMembership(newGroupMembership);
        }
    }

}
