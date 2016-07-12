import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.JobStatus;
import org.zendesk.client.v2.model.JobStatus.JobStatusEnum;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.User;

public class UniconZendeskImport {

    private static final Logger log = LoggerFactory.getLogger(UniconZendeskImport.class);

    private static Zendesk fromZendesk;
    private static Zendesk toZendesk;
    private static String toUrl = "https://unicon.zendesk.com";
    private static String fromUrl = "https://uni-trial.zendesk.com";
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
    /**
     * Individual users to import
     */
    private static Long[] userIds = new Long[] {
    };

    public static void main(String[] args) {
        fromZendesk = new Zendesk.Builder(fromUrl)
            .setUsername("rlong@unicon.net")
            .setToken("4anl5E9L4jaDfsTmTmePoF71MYAbst8ubxcUnqVg")
            .build();

        toZendesk = new Zendesk.Builder(toUrl)
            .setUsername("zd_trial@yahoo.com")
            .setToken("ZrXsSEEHDLQATGl4CCNUSvNJ8rAMxCKYd14n4OO5")
            .build();

        importOrganizations();
    }

    private static void importOrganizations() {
        for (Long fromOrganizationId : organizationIds) {
            Organization fromOrganization = fromZendesk.getOrganization(fromOrganizationId);

            if (fromOrganization == null) {
                log.error("No organization found with ID {}", Long.toString(fromOrganizationId));
                continue;
            }

            // import organization
            Organization newOrganization = importOrganization(fromOrganization);

            // import users from organization
            Iterable<User> organizationUsers = fromZendesk.getOrganizationUsers(fromOrganizationId);

            importUsersIntoOrganization(organizationUsers, newOrganization.getId());
        }
    }

    private static Organization importOrganization(Organization fromOrganization) {
        Organization newOrganization = new Organization();

        return toZendesk.createOrganization(newOrganization);
    }

    private static void importUsersIntoOrganization(Iterable<User> fromUsers, long organizationId) {
        List<User> newUsers = new ArrayList<>();

        for (User user : fromUsers) {
            User newUser = new User();
            newUser.setName(user.getName());
            newUser.setActive(user.getActive());
            newUser.setAlias(user.getAlias());
            newUser.setVerified(user.getVerified());
            newUser.setEmail(user.getEmail());
            newUser.setPhone(user.getPhone());
            newUser.setSignature(user.getSignature());
            newUser.setDetails(user.getDetails());
            newUser.setNotes(user.getNotes());
            newUser.setOrganizationId(organizationId);
            newUser.setRole(user.getRole());
            newUser.setModerator(user.getModerator());
            newUser.setPhoto(user.getPhoto());
            newUser.setUserFields(user.getUserFields());

            newUsers.add(newUser);
        }

        JobStatus<User> jobStatus = toZendesk.createUsers(newUsers);

        while (JobStatus.JobStatusEnum.working == jobStatus.getStatus() || JobStatus.JobStatusEnum.queued == jobStatus.getStatus()) {
            log.info("Creating users:: users created: {}", jobStatus.getProgress());
            try {
                Thread.sleep(2000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            jobStatus = toZendesk.getJobStatus(jobStatus);

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
            log.info("Creating/updating user on {}:: name: {}, email: {}, organization id: {}, ", toUrl, newUser.getName(), newUser.getEmail(), organizationId);
        }
    }

}
