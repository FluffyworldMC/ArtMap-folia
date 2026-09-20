![ArtMap](http://puu.sh/kRWAF/2c81256338.jpg)

### Supported Versions

* Paper 26.1 and 1.21 - Master Branch. Every commit is tested against a real Paper 26.1.2 server with
  a scripted client (easels, painting, the paintbrush save dialog, menus, commands); 1.21.11 went through the
  same suite before it. Both need the current ProtocolLib development build. Paper 26.2 and later are not
  tested yet.
* Spigot/Paper 1.20.2 - 1.20.6, 1.19, 1.18.1, 1.17.1, 1.16.1, 1.15.2, 1.14.4, 1.13.2 - Master Branch, built
  against but not covered by the automated server tests.
* Spigot/Paper 1.8.8 - 1.12.2 - 1.12 Branch

### Downloads

<a href="https://gitlab.com/BlockStack/ArtMap/-/packages">
<img src="/uploads/1507dccfd03a212b8544848cd49a24ad/image.png"/></a>

* Downloads are available on the releases page in gitlab.
* Downloads are built for two JDKs. Use the one that matches the Java your server runs on.
  * 1.13-1.19 - Use the JDK17 version.
  * 1.20, 1.21 and 26.x - Use the JDK21 version (Paper 26.x itself runs on Java 25; the JDK21 build works there).

### Features

* Custom easel entity
* Basic filter for artwork titles
* Asynchronous protocol handling
* List & preview system to view artworks

### Permissions Nodes

* artmap.artist - allows players to use artmap
* artmap.admin - grants administrative override/deletion priveleges
* artmap.artkit - Will give players access to all dyes when seated at an easel and the config option forceartkit is set to true.

## Move to Gitlab

I've moved this project to gitlab https://gitlab.com/BlockStack/ArtMap.  Mirroring has been setup so all commits should be available to fork on github.  But please submit issues on Gitlab.

### Attribution

This is not my original work and belongs to Fupery.  I continue maintenance of this plugin in his absence for my personal use.  As he had the code freely available I have continued that with my updates.

Bukkit plugin - allows players to draw directly onto minecraft maps. 
User guide at [ArtMap Wiki](https://gitlab.com/BlockStack/ArtMap/wikis/home).

### Build and Coverage

[![pipeline](https://gitlab.com/BlockStack/ArtMap/badges/master/pipeline.svg)](https://gitlab.com/BlockStack/ArtMap/-/pipelines)
[![coverage](https://gitlab.com/BlockStack/ArtMap/badges/master/coverage.svg)](https://blockstack.gitlab.io/ArtMap/coverage/)

The coverage figure merges the unit tests with the integration run against a real Paper server; the badge
links to the full JaCoCo report for the master branch.
