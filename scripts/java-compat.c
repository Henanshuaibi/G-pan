/* Build-host compatibility only; never included in the APK. */
#define _GNU_SOURCE
#include <sys/syscall.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stddef.h>
int ASharedMemory_create(const char *name,size_t size) {
 int fd=syscall(SYS_memfd_create,name?name:"jdk-shmem",1);
 if(fd<0)return -1;
 if(ftruncate(fd,size)<0){close(fd);return -1;}
 return fd;
}
size_t ASharedMemory_getSize(int fd) {
 struct stat info;return fstat(fd,&info)==0?(size_t)info.st_size:0;
}
