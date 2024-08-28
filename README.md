Best viewed in a Markdown reader [Chrome](https://chrome.google.com/webstore/search/markdown%20reader?hl=en-GB)/[Firefox](https://addons.mozilla.org/en-us/firefox/search/?q=Markdown+Viewer)

# Setup

- Create a new vApp based on the vApp Catalog template **ENM_MainTrack:mt_ib**
    - If this isn't available in your cloud area open a support ticked with [Continuous Integration Services](http://jira-nam.lmera.ericsson.se/browse/CIS/?selectedTab=com.atlassian.jira.jira-projects-plugin:summary-panel) and get them to copy the `mt_ib` template from [POD-C](https://atvpcspp12.athtem.eei.ericsson.se) to your POD.
- Once created, log in as **lciadm100** and regenerate the ssh keys.
- Log in to [Gerrit](https://gerrit.ericsson.se/) using your Ericsson ID and 
add the contents of the `/home/lciadm100/.ssh/id_rsa.pub` file to your list
of **SSH Public Keys**.
- Clone down one of the ENM image git repos:
    - [ERICrhel6baseimage](https://gerrit.ericsson.se/#/admin/projects/OSS/com.ericsson.oss.images/ERICrhel6baseimage)
    - [ERICrhel6jbossimage](https://gerrit.ericsson.se/#/admin/projects/OSS/com.ericsson.oss.images/ERICrhel6jbossimage)

# Building Snapshot Images (Basic)
`cd` into an ENM VM image repo and run the following command to build a snapshot .qcow file.
>`mvn -e install -PbuildImage`

This will build 2 qcow2 files to `/dev/shm/` as 
>`<CXP_Name>_<Version>-SNAPSHOT.qcow2`

and a compressed version of the same image file

>`compressed_<CXP_Name>_<Version>-SNAPSHOT.qcow2`
 
e.g.
> `ERICrhel6baseimage_CXP9031559-2.0.5-SNAPSHOT.qcow2`

> `compressed_ERICrhel6baseimage_CXP9031559-2.0.5-SNAPSHOT.qcow2`

You can then take that image file and install/upgrade using a cracked ENM ISO.

You can also start a local KVM using the snapshot image to check things out.

As `root`:
 
>`cd /dev/shm/`</br>
>`virsh create kvm_domain.xml`</br>
>`virsh console <vm_name>`</br>

You'll now be conntected to the console of the VM and eventually get a login prompt, you can then log in using `root/passw0rd`

# Building Snapshot Images (Advanced)
## Overview
There are a couple of maven properties are used to configure the image customization process
- `build.type`
  
  The type of build, either `create` or `customize`.
  
  Use `create` to take an RHEL iso and convert it to a `.qcow2` format image.
  
  Use `customize` to take an existing image and modify it for your own purposes.

- `build.dir`

    A directory to use to buld the images. Should have enough space to hold all required files
    e.g. parent image or source ISO.
    
    Default is `/dev/shm`
    
- `seed.iso`

    An iso containing `cloud-init` data needed to build the image.
    
- `osName`

    The OS being installed.
    
- `osVersion`

    The OS version being installed.
    
- `osArch`

    The arch of the OS being installed.
    
- `set.disk`

    Used to set the size of the root disk of the image.
    
- `set.rootpass`

    Used to set the password for root
    
- `kickstart`
    
    Used to specify a custome kickstart file when `build.type=create`
    
- `yum_repo.<NAME>`

    You can add static repo to the build using named properties.
    
    e.g. `yum_repo.rhel6_patches=http://192.168.0.1/snapshot` should result in a 
     yum repo with `name=rhel6_patches` added to the image, packages from this repo can then be installed later
    Example xml in pom file:
        <yum_repo.rhel-dvd>https://ci-portal.seli.wh.rnd.internal.ericsson.com/static/staticRepos/RHEL6.6/dvd/</yum_repo.rhel-dvd>
     
- `product_repo.<PRODUCT>`
    
    Create a temporary yum repo from a Product ISO e.g. `product_repo.ENM=latest.Maintrack` would result in a 
    yum repo being defined for the product `ENM` with versions of packages from `latest.Maintrack` 
    Example xml in pom file:
        <product_repo.ENM>latest.Maintrack</product_repo.ENM>
     
- `artifact.to.install`

    Global comma seperated list of rpms to install. These packages will be sourced from any 
    existing repos on the parent image or from repos listed in the `yum_repo.<NAME>` or `product_repo.<PRODUCT>` properties.

- `kgb.package.list.<PRODUCT>`
    
    List of KBG package versions (packages that have been been released to Nexus but not to the the Product)
    
    e.g. Product ENM has an rpm ERICexample-1.2.3 released to the ISO. If you want to build an image with ERICexample-1.2.4, which is in Nexus but
    not yet released to the ENM Iso, then you can include that version in the ENM temporary repo with `product_repo.ENM=latest.Maintrack` and 
    `kgb.package.list.ENM=ERICexample::1.2.3`

## Build examples

Examples assume the the following are set in the images `pom.xml`
>`product_repo.ENM=latest.Maintrack`

>`artifact.to.install=ERICenmconfiguration_CXP9031455`

and `ERICenmconfiguration_CXP9031455-1.4.7` has been released to the ENM iso


### Building an image with product packages in Nexus but not yet on a product ISO.

The package `ERICenmconfiguration_CXP9031455-1.4.7` is on the `ENM` iso, you want to build an image but use 
 `ERICenmconfiguration_CXP9031455-1.5.0` which is avaiable in Nexus (i.e. the Jenkins release job has been executed 
 but the package has not been queued and passed MainTrack)
 
 >`mvn -e install -PbuildImage -Dproduct_repo.ENM=latest.Maintrack -Dkgb.package.list.ENM=ERICenmconfiguration_CXP9031455::15.0`
 
 This will result in the package ERICenmconfiguration_CXP9031455-1.15.0 being added to the ENM temp repo (replacing the version 
 that's delivered to the ENM iso).
 
### Building a snapshot image with snapshot versions of packages not yet released to Nexus.

You've a snapshot version of `ERICenmconfiguration_CXP9031455-1.4.8-SNAPSHOT` you want to test, you can create a local yum repo and add the snapshot 
package to it and include the local repo to the build process.

Create a local YUM repo and add the snapshot rpm to the repo: _If `createrepo` isn't found then `yum install -y createrepo`_
>`mkdir -p /var/www/html/snapshot_repo`</br>
>`cp ERICenmconfiguration_CXP9031455-1.4.8-SNAPSHOT.rpm /var/www/html/snapshot_repo`</br>
>`createrepo /var/www/html/snapshot_repo`</br>
>`service httpd restart`

Build the image and include the snapshot_repo:
>`mvn -e install -PbuildImage -Dyum_repo.snapshot=http://192.168.0.1/snapshot_repo`

Since `ERICenmconfiguration` is listed in `artifact.to.install` YUM will take the latest version of a package, in this case `1.4.8-SNAPSHOT`

### Building an image with snapshot versions of packages not yet released to Nexus nor ever delivered to an ENM ISO.

The verion of these RPM's doesn't matter, you just need to tell the oz-customize process the package names and where to get them.

Create a local yum repo and add the packages there.
>`mkdir -p /var/www/html/snapshot_repo`</br>
>`cp ERICsomepackage-1.0.0.rpm /var/www/html/snapshot_repo`</br>
>`createrepo /var/www/html/snapshot_repo`</br>
>`service httpd restart`

Include the `snapshot_repo` and the new package to the build process:
>`mvn -e install -PbuildImage -Dyum_repo.snapshot=http://192.168.0.1/snapshot_repo -Dartifact.to.install=ERICsomepackage`

This will result in a snapshot image with `ERICsomepackage` installed.




    
 