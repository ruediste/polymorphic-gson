# Type adapter to support polymorphic deserialization with Gson

For a high level overview refer to my [blog](https://ruediste.github.io/java/gson/2020/04/29/polymorphic-json-with-gson.html).

## Usage
Refer to the maven artifact

```
<dependency>
	<groupId>com.github.ruediste</groupId>
	<artifactId>polymorphic-gson</artifactId>
	<version>0.0.1-SNAPSHOT</version>
</dependency>
```

Then register the type adapter when you create your Gson instance: 

``` java
new GsonBuilder().registerTypeAdapterFactory(
	PolymorphStyle.TYPE_PROPERTY,
	getClass().getClassLoader(), 
	"your.package.prefix.to.scan"))
	...
	.create();
```

## Releasing
Use 

```
mvn versions:set -DnewVersion=0.1
```

Push to the `release` branch. A github action will automatically pick this up and publish the artifacts. Then run a  

```
mvn versions:set -DnewVersion=0.2-SNAPSHOT
```

and push to master.