# FileDuplicateRemover
Calculate the SHA-256 hash of files &amp; removes duplicates

## Usage

parameters are 
`java fdr.jar <path>`

**path** is the directory to search and remove duplicates
In each searched subdirectory a ".folder_info" is created to optimize folder scanning and to first search in folders with changes
at `path/.duplicate_info` a `blacklist.regex` and a `hashes2file.map` file will appear. 

### blacklisting files & folders

use the `path/.duplicate_info/blacklist.regex` to make a blacklist using regular expressions

### haches2file.map
in this file all the so far calculated file hashes are stored.