 Failed to initialize JPA EntityManagerFactory: Unable to build Hibernate SessionFactory  [persistence unit: default] ; nested exception is org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing table [users]
 
 
 Error starting Tomcat context. Exception: org.springframework.beans.factory.UnsatisfiedDependencyException. Message: Error creating bean with name 'jwtAuthenticationFilter' defined in file [/home/renmakara/Documents/aub/campuslink_kh/campuslink_kh/build/classes/java/main/kh/edu/acleda/campuslink/shared/security/JwtAuthenticationFilter.class]: Unsatisfied dependency expressed through constructor parameter 1: Error creating bean with name 'customUserDetailsService' defined in file [/home/renmakara/Documents/aub/campuslink_kh/campuslink_kh/build/classes/java/main/kh/edu/acleda/campuslink/shared/security/CustomUserDetailsService.class]: Unsatisfied dependency expressed through constructor parameter 0: Error creating bean with name 'userRepository' defined in kh.edu.acleda.campuslink.modules.user.UserRepository defined in @EnableJpaRepositories declared on DataJpaRepositoriesRegistrar.EnableJpaRepositoriesConfiguration: Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory' while setting bean property 'entityManager'

 Error creating bean with name 'jwtAuthenticationFilter
 
  Error creating bean with name 'customUserDetailsService'
  
  Error creating bean with name 'userRepository'
  
  Error creating bean with name 'entityManagerFactory'

Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing table [users]
	at org.hibernate.tool.schema.internal.AbstractSchemaValidator.validateTable(AbstractSchemaValidator.java:121) ~[hibernate-core-7.2.12.Final.jar:7.2.12.Final]
	at org.hibernate.tool.schema.internal.GroupedSchemaValidatorImpl.validateTables(GroupedSchemaValidatorImpl.java:42) ~[hibernate-core-7.2.12.Final.jar:7.2.12.Final]
	at org.hibernate.tool.schema.internal.AbstractSchemaValidator.performValidation(AbstractSchemaValidator.java:90) ~[hibernate-core-7.2.12.Final.jar:7.2.12.Final]
	at org.hibernate.tool.schema.internal.AbstractSchemaValidator.doValidation(AbstractSchemaValidator.java:68) ~[hibernate-core-7.2.12.Final.jar:7.2.12.Final]
	at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.performDatabaseAction(SchemaManagementToolCoordinator.java:278) ~[hibernate-core-7.2.12.Final.jar:7.2.12.Final]
	at org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator.lambda$process$1(SchemaManagementToolCoordinator.java:101) ~[hibernate-core-7.2.12.Final.jar:7.2.12.Final]
	at java.base/java.util.HashMap.forEach(HashMap.java:1429) ~[na:na]
