'use client';

import { ApiHairFastGan } from "@/common/constant/api-url.constant";
import ListHairColorImageShow from "@/components/ListHairColorImageShow";
import Modal from "@/components/modal/Modal";
import ModalHairStyleImageGroup from "@/components/ModalHairStyleImageGroup";
import React from "react";
import { useRef, useState } from "react";
import Swal from "sweetalert2";

export default function HairFastGanPage() {
  const choosedHairStyleImageUrlRef = useRef<any>();
  const hairStyleImageElementRef = useRef<any>();
  const hairStyleNameElementRef = useRef<any>();
  const hairStyleNametRef = useRef<any>();
  const [hairStyleImage, setHairStyleImage] = useState<any>(null);

  const choosedHairColorImageUrlRef = useRef<any>();
  const hairColorImageElementRef = useRef<any>();
  const hairColorNameElementRef = useRef<any>();

  const yourFaceImageRef = useRef<any>();
  const preYourFaceImageFileRef = useRef<any>();

  const resultImageElementRef = useRef<any>();

  const handleYourFaceInputChange = (e: any) => {
    URL.revokeObjectURL(preYourFaceImageFileRef.current || null);
    preYourFaceImageFileRef.current = e.target.files[0];
    yourFaceImageRef.current.src = preYourFaceImageFileRef.current ? URL.createObjectURL(preYourFaceImageFileRef.current) : '/img/fb-no-img.png';
  }

  const handleSwapHair = () => {
    if (!preYourFaceImageFileRef.current || hairColorImageElementRef.current.src === '/img/fb-no-img.png' || hairStyleImageElementRef.current.src === '/img/fb-no-img.png') {
      Swal.fire({
        icon: "error",
        title: "Oops...",
        text: "Your face image or hair color or hair style is not empty!",
      });
      return;
    }

    const formdata = new FormData();
    formdata.append("hairStyleUrl", hairStyleImageElementRef.current.src);
    formdata.append("hairColorUrl", hairColorImageElementRef.current.src);
    formdata.append("image", preYourFaceImageFileRef.current);

    Swal.fire({
      title: "Processing...",
      timerProgressBar: true,
      didOpen: () => {
        Swal.showLoading();
      },
    });

    fetch(ApiHairFastGan.SWAP_HAIR, {
      method: "POST",
      body: formdata,
      redirect: "follow"
    })
      .then((response) => {
        if (response.status !== 200 && response.status !== 201) {
          Swal.close();
          Swal.fire({
            icon: "error",
            title: "Oops...",
            text: "Something went wrong!",
          });
          return;
        }
        return response.blob();
      })
      .then((result) => {
        const imageUrl = URL.createObjectURL(result as any);
        resultImageElementRef.current.src = imageUrl;
        Swal.close();
      })
      .catch((error) => {
        console.log(error);
        Swal.close();
        Swal.fire({
          icon: "error",
          title: "Oops...",
          text: "Something went wrong!",
        });
      });
  }

  const handleDownloadResult = () => {
    const downloadUrl = resultImageElementRef.current.src;
    if (downloadUrl && downloadUrl !== '/img/fb-no-img.png') {
      const link = document.createElement('a');
      link.href = downloadUrl;
      
      link.setAttribute('download', 'result.png');
      document.body.appendChild(link);
      
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(downloadUrl);
    }
  }

  return (
    <>
      <ModalHairStyleImageGroup
        hairStyleImage={hairStyleImage}
        setHairStyleImage={setHairStyleImage}
        choosedHairStyleImageUrlRef={choosedHairStyleImageUrlRef}
        hairStyleImageElementRef={hairStyleImageElementRef}
        hairStyleNameElementRef={hairStyleNameElementRef}
        hairStyleNametRef={hairStyleNametRef}
      />

      <Modal title="Hair color" id="hair-color-modal">
        <ListHairColorImageShow
          choosedHairColorImageUrlRef={choosedHairColorImageUrlRef}
        />
        <button
          type="button"
          className="mx-auto text-white bg-blue-700 hover:bg-blue-800 focus:ring-4 focus:outline-none focus:ring-blue-300 font-medium rounded-lg text-sm px-5 py-2.5 text-center dark:bg-blue-600 dark:hover:bg-blue-700 dark:focus:ring-blue-800"
          data-modal-hide="hair-color-modal"
          onClick={() => {
            hairColorImageElementRef.current.src = choosedHairColorImageUrlRef.current.url;
            hairColorNameElementRef.current.style.color = choosedHairColorImageUrlRef.current.color;
            hairColorNameElementRef.current.innerText = choosedHairColorImageUrlRef.current.color;
          }}
        >
          OK
        </button>
      </Modal>

      <div className="bg-gray-100">
        <div className="container mx-auto p-4">
          <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            <label htmlFor="you-face-input">
              <input
                type="file"
                id="you-face-input"
                className="hidden"
                accept="image/*"
                onChange={handleYourFaceInputChange}
              />
              <div title="Click to choose file" className="w-full hover:cursor-pointer rounded-lg border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div className="h-56 w-full">
                  <div style={{ width: '100%', height: '100%' }}>
                    <img
                      className="m-auto h-full dark:hidden"
                      src="/img/fb-no-img.png"
                      alt=""
                      style={{ objectFit: 'cover' }}
                      ref={yourFaceImageRef}
                    />
                  </div>
                </div>
              </div>
              <div className="mt-2 text-sm text-center text-gray-500 dark:text-gray-400">Your face</div>
            </label>

            <div>
              <div data-modal-target="hair-style-modal" data-modal-toggle="hair-style-modal" title="Click to choose hair style" className="w-full hover:cursor-pointer rounded-lg border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div className="h-56 w-full">
                  <div style={{ width: '100%', height: '100%' }}>
                    <img
                      className="m-auto h-full dark:hidden"
                      src="/img/fb-no-img.png"
                      alt=""
                      style={{ objectFit: 'cover' }}
                      ref={hairStyleImageElementRef}
                    />
                  </div>
                </div>
              </div>
              <div
                className="mt-2 text-sm text-center text-gray-500 dark:text-gray-400"
                ref={hairStyleNameElementRef}
              >
                Hair style
              </div>
            </div>

            <div>
              <div data-modal-target="hair-color-modal" data-modal-toggle="hair-color-modal" title="Click to choose hair color" className="w-full hover:cursor-pointer rounded-lg border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div className="h-56 w-full">
                  <div style={{ width: '100%', height: '100%' }}>
                    <img
                      className="m-auto h-full dark:hidden"
                      src="/img/fb-no-img.png"
                      alt=""
                      style={{ objectFit: 'cover' }}
                      ref={hairColorImageElementRef}
                    />
                  </div>
                </div>
              </div>
              <div
                className="mt-2 text-sm text-center text-gray-500 dark:text-gray-400"
                ref={hairColorNameElementRef}
              >
                Hair color
              </div>
            </div>

            <div>
              <div title="Result" className="w-full rounded-lg border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div className="h-56 w-full">
                  <div style={{ width: '100%', height: '100%' }}>
                    <img
                      className="m-auto h-full dark:hidden"
                      src="/img/fb-no-img.png"
                      alt=""
                      style={{ objectFit: 'cover' }}
                      ref={resultImageElementRef}
                    />
                  </div>
                </div>
              </div>
              <div
                className="mt-2 text-sm text-center text-primary-700 hover:underline hover:cursor-pointer dark:text-primary-500"
                onClick={handleDownloadResult}
              >
                Download
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="w-full flex items-center justify-center">
        <button
          type="button"
          className="focus:outline-none text-white bg-green-700 hover:bg-green-800 focus:ring-4 focus:ring-green-300 font-medium rounded-lg text-sm px-5 py-2.5 mt-8 me-2 mb-2 dark:bg-green-600 dark:hover:bg-green-700 dark:focus:ring-green-800"
          onClick={handleSwapHair}
        >
          Try now!
        </button>
      </div>
    </>
  );
}