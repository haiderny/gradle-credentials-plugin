package nu.studer.gradle.credentials;

import nu.studer.gradle.credentials.domain.CredentialsContainer;
import nu.studer.gradle.credentials.domain.CredentialsEncryptor;
import nu.studer.gradle.credentials.domain.CredentialsPersistenceManager;
import nu.studer.gradle.util.MD5;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

/**
 * Plugin to store and access encrypted credentials.
 */
public class CredentialsPlugin implements Plugin<Project> {

    public static final String DEFAULT_PASSPHRASE_CREDENTIALS_FILE = "gradle.encrypted.properties";
    public static final String DEFAULT_PASSPHRASE = ">>Default passphrase to encrypt passwords!<<";

    public static final String CREDENTIALS_CONTAINER_PROPERTY = "credentials";

    public static final String CREDENTIALS_PASSPHRASE_PROPERTY = "credentialsPassphrase";
    public static final String CREDENTIALS_KEY_PROPERTY = "credentialsKey";
    public static final String CREDENTIALS_VALUE_PROPERTY = "credentialsValue";

    public static final String ADD_CREDENTIALS_TASK_NAME = "addCredentials";
    public static final String REMOVE_CREDENTIALS_TASK_NAME = "removeCredentials";

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsPlugin.class);

    @Override
    public void apply(Project project) {
        // derive the name of the credentials file from the (optionally) given passphrase
        String credentialsFileName = deriveFileNameFromPassphrase(project);

        // create credentials encryptor for the given passphrase
        CredentialsEncryptor credentialsEncryptor = CredentialsEncryptor.withPassphrase(CredentialsPlugin.DEFAULT_PASSPHRASE.toCharArray());

        // create a credentials persistence manager that operates on the credentials file
        File gradleUserHomeDir = project.getGradle().getGradleUserHomeDir();
        File credentialsFile = new File(gradleUserHomeDir, credentialsFileName);
        CredentialsPersistenceManager credentialsPersistenceManager = new CredentialsPersistenceManager(credentialsFile);

        // add a new 'credentials' property and transiently store the persisted credentials for access in build scripts
        Properties persistedCredentials = credentialsPersistenceManager.readCredentials();
        CredentialsContainer credentialsContainer = new CredentialsContainer(persistedCredentials);
        project.getExtensions().getExtraProperties().set(CREDENTIALS_CONTAINER_PROPERTY, credentialsContainer);
        LOGGER.debug("Registered property '" + CREDENTIALS_CONTAINER_PROPERTY + "'");

        // add a task instance that stores new credentials through the credentials persistence manager
        AddCredentialsTask addCredentials = project.getTasks().create(ADD_CREDENTIALS_TASK_NAME, AddCredentialsTask.class);
        addCredentials.setDescription("Adds the credentials specified through the project properties 'credentialsKey' and 'credentialsValue'.");
        addCredentials.setGroup("Credentials");
        addCredentials.setCredentialsEncryptor(credentialsEncryptor);
        addCredentials.setCredentialsPersistenceManager(credentialsPersistenceManager);
        LOGGER.debug(String.format("Registered task '%s'", addCredentials.getName()));
    }

    private String deriveFileNameFromPassphrase(Project project) {
        // get the optionally specified passphrase from the project properties
        String passphrase = getProjectProperty(CREDENTIALS_PASSPHRASE_PROPERTY, project);

        // derive the name of the file that contains the credentials from the given passphrase
        String credentialsFileName;
        if (passphrase == null) {
            credentialsFileName = DEFAULT_PASSPHRASE_CREDENTIALS_FILE;
            LOGGER.debug("No explicit passphrase provided. Using default credentials file name: " + credentialsFileName);
        } else {
            credentialsFileName = "gradle." + MD5.generateMD5Hash(passphrase) + ".encrypted.properties";
            LOGGER.debug("Custom passphrase provided. Using credentials file name: " + credentialsFileName);
        }
        return credentialsFileName;
    }

    private String getProjectProperty(String key, Project project) {
        return (String) project.getProperties().get(key);
    }

}